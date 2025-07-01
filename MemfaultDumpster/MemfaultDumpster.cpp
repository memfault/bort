#define LOG_TAG "MemfaultDumpster"

#include <android-base/file.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/PersistableBundle.h>
#include <binder/ProcessState.h>
#include <DumpstateUtil.h>
#include <log/log_main.h>
#include <com/memfault/dumpster/BnDumpster.h>
#include <com/memfault/dumpster/IDumpsterBasicCommandListener.h>

#include <stdlib.h>

#include <memory>
#include <string>
#include <vector>

#include <dirent.h>
#include <unistd.h>

#include "android-9/file.h"
#include "ContinuousLogcat.h"
#include "storage.h"

#define DUMPSTER_SERVICE_NAME "memfault_dumpster"
#define BORT_ENABLED_PROPERTY "persist.system.memfault.bort.enabled"
#define STRUCTURED_ENABLED_PROPERTY "persist.system.memfault.structured.enabled"

using android::os::PersistableBundle;
using android::os::dumpstate::CommandOptions;
using android::os::dumpstate::RunCommandToFd;

using com::memfault::dumpster::BnDumpster;
using com::memfault::dumpster::IDumpster;
using com::memfault::dumpster::IDumpsterBasicCommandListener;

namespace {
  int RunCommandToString(const std::vector<std::string>& command, std::string &output) {
      TemporaryFile tempFile;
      const int rv = RunCommandToFd(tempFile.fd, "", command,
                                    CommandOptions::WithTimeout(20)
                                    .Always()
                                    .Build());
      android::base::ReadFileToString(tempFile.path, &output);
      return rv;
  }

  std::set<pid_t> allPids() {
    std::set<pid_t> pids;
    DIR *dir = opendir("/proc");
    if (!dir) {
      return pids;
    }
    dirent *entry;
    while ((entry = readdir(dir)) != nullptr) {
      if (entry->d_type == DT_DIR) {
        // we're not interested in non-numeric entries such as /proc/stat and /proc/self
        std::string name = entry->d_name;
        if (name.find_first_not_of("0123456789") == std::string::npos) {
          pids.insert(std::stoi(name));
        }
      }
    }
    closedir(dir);
    return pids;
  }

  std::string readProcPid(int pid) {
    std::string procPid;
    if (!android::base::ReadFileToString("/proc/" + std::to_string(pid) + "/stat", &procPid)) {
      // Some of these are bound to fail due to process termination between listing pids
      // and reading their stat entry
      return "";
    }
    return procPid;
  }

  int readUid(int pid) {
    std::string status;
    if (!android::base::ReadFileToString("/proc/" + std::to_string(pid) + "/status", &status)) {
        return -1;
    }
    /**
     * Output format is:
     * Name:   colord
     * Umask:  0022
     * State:  S (sleeping)
     * Tgid:   6007
     * Ngid:   0
     * Pid:    6007
     * PPid:   1
     * TracerPid:      0
     * Uid:    961     961     961     961
     * Gid:    961     961     961     961
     * FDSize: 256
     * Groups: 961
     * (...)
     *
     * We only care about Uid
     */

    int uid;
    std::istringstream statusStream(status);
    std::string line;
    while (std::getline(statusStream, line)) {
        if (line.find("Uid:") == 0) {
            std::istringstream uidStream(line);

            // consume the label
            std::string uidLabel;
            uidStream >> uidLabel;

            // consume the first value (ruid)
            uidStream >> uid;
            return uid;
        }
    }

    return -1;
  }

  int readProcPidStats(std::string& output) {
    std::stringstream buffer;
    for (int pid : allPids()) {
      int uid = readUid(pid);
      if (uid == -1) {
        continue;
      }
      std::string procPid = readProcPid(pid);
      if (!procPid.empty()) {
        buffer << uid << " " << procPid << std::endl;
      }
    }
    output = buffer.str();
    return 0;
  }

  int readStorageWear(std::string& output) {
    memfault::jedec_storage_info info;

    if (!get_storage_info(info)) {
      return -1;
    }

    std::stringstream buffer;
    buffer << info.eol << " " << info.lifetimeA << " " << info.lifetimeB << " " << info.source << " " << info.version;
    output = buffer.str();
    return 0;
  }

  class DumpsterService : public BnDumpster {
        public:
        DumpsterService() : clog(new memfault::ContinuousLogcat()) {
        }

        android::binder::Status getVersion(int *_aidl_return) {
          *_aidl_return = IDumpster::VERSION;
          return android::binder::Status::ok();
        }

        using CommandFunc = std::function<int(std::string&)>;

        android::binder::Status runBasicCommand(
            int cmdId, const android::sp<IDumpsterBasicCommandListener> &listener) override {
            auto commandFunc = commandFuncForId(cmdId);
            if (!commandFunc) {
              listener->onUnsupported();
            } else {
              std::string output;
              const int rv = commandFunc(output);
#if PLATFORM_SDK_VERSION <= 30
              listener->onFinished(rv, std::make_unique<android::String16>(output.c_str()));
#else
              listener->onFinished(rv, android::String16(output.c_str()));
#endif
            }
            return android::binder::Status::ok();
        }

        CommandFunc commandFuncForId(int cmdId) {
            switch (cmdId) {
                case IDumpster::CMD_ID_GETPROP: return cmdToStringFunc({ "/system/bin/getprop" });
                case IDumpster::CMD_ID_GETPROP_TYPES: return cmdToStringFunc({ "/system/bin/getprop", "-T" });
                case IDumpster::CMD_ID_SET_BORT_ENABLED_PROPERTY_ENABLED: return cmdToStringFunc({
                  "/system/bin/setprop", BORT_ENABLED_PROPERTY, "1"
                });
                case IDumpster::CMD_ID_SET_BORT_ENABLED_PROPERTY_DISABLED: return cmdToStringFunc({
                  "/system/bin/setprop", BORT_ENABLED_PROPERTY, "0"
                });
                case IDumpster::CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_ENABLED: return cmdToStringFunc({
                  "/system/bin/setprop", STRUCTURED_ENABLED_PROPERTY, "1"
                });
                case IDumpster::CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_DISABLED: return cmdToStringFunc({
                  "/system/bin/setprop", STRUCTURED_ENABLED_PROPERTY, "0"
                });
                case IDumpster::CMD_ID_CYCLE_COUNT_NEVER_USE: return cmdToStringFunc({
                  "echo", ""
                });
                case IDumpster::CMD_ID_PROC_STAT: return cmdToStringFunc({
                  "cat", "/proc/stat"
                });
                case IDumpster::CMD_ID_PROC_PID_STAT: {
                  return [](std::string& output) { 
                    return readProcPidStats(output);
                  };
                }
                case IDumpster::CMD_ID_STORAGE_WEAR: {
                  return [](std::string& output) {
                    return readStorageWear(output);
                  };
                }


                default: return nullptr; 
            }
        }

        android::binder::Status startContinuousLogging(
            const PersistableBundle &options) override {
          int32_t version;
          if (options.getInt(android::String16("version"), &version) && version == 1) {
            std::vector<android::String16> filter_specs_s16;
            options.getStringVector(android::String16("filterSpecs"), &filter_specs_s16);

            // unpack String16-format strings
            std::vector<std::string> filter_specs;
            for (auto &it : filter_specs_s16) {
#if PLATFORM_SDK_VERSION <= 34
              const char* spec = android::String8(it).string();
#else
              const char* spec = android::String8(it);
#endif
              filter_specs.emplace_back(spec);
            }

            int32_t dump_threshold_bytes;
            if (!options.getInt(android::String16("dumpThresholdBytes"), &dump_threshold_bytes)) {
              dump_threshold_bytes = kDefaultDumpThresholdBytes;
            }

            int64_t dump_threshold_time_ms;
            if (!options.getLong(android::String16("dumpThresholdTimeMs"), &dump_threshold_time_ms)) {
              dump_threshold_time_ms = kDefaultDumpThresholdTimeMs;
            }

            int64_t dump_wrapping_timeout_ms;
            if (!options.getLong(android::String16("dumpWrappingTimeoutMs"), &dump_wrapping_timeout_ms)) {
              dump_wrapping_timeout_ms = kDefaultDumpWrappingTimeoutMs;
            }

            ALOGT("clog: reconfiguring");
            clog->reconfigure(filter_specs, dump_threshold_bytes, (uint64_t)dump_threshold_time_ms,
                (uint64_t)dump_wrapping_timeout_ms);
          } else {
            ALOGW("Cannot parse reconfiguration options, starting with current config");
          }

          ALOGT("clog: starting");
          clog->start();
          ALOGT("clog: started");
          return android::binder::Status::ok();
        }

        android::binder::Status stopContinuousLogging() override {
          ALOGT("clog: stopping");
          clog->stop();
          ALOGT("clog: joining");
          clog->join();
          ALOGT("clog: stopped");
          return android::binder::Status::ok();
        }

        void requestContinuousLogDump() {
          ALOGT("clog: requesting dump");
          clog->request_dump();
          ALOGT("clog: requesting dump done");
        }

    private:
        std::unique_ptr<memfault::ContinuousLogcat> clog;

        CommandFunc cmdToStringFunc(const std::vector<std::string>& command) {
          return [command](std::string& output) { return RunCommandToString(command, output); };
        }
    };
} // namespace

static void uncaught_handler(int signum __unused) {}

void watch_dump_signal(DumpsterService *service) {
        sigset_t set;
        sigemptyset(&set);
        sigaddset(&set, SIGUSR1);

        int signum;
        while (service) {
          if (!sigwait(&set, &signum) && service) {
            ALOGT("clog: got request_dump");
            service->requestContinuousLogDump();
          }
        }
}

int main(void) {
    ALOGI("Starting...");

    struct sigaction sa;
    sa.sa_handler = uncaught_handler;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGPIPE, &sa, NULL);
    sigaction(SIGALRM, &sa, NULL);

    sigset_t blockset;
    sigemptyset(&blockset);
    sigaddset(&blockset, SIGUSR1);
    sigprocmask(SIG_BLOCK, &blockset, NULL);

    android::sp<android::ProcessState> ps(android::ProcessState::self());
    ps->setThreadPoolMaxThreadCount(1);
    ps->startThreadPool();
    ps->giveThreadPoolName();

    DumpsterService *dumpsterService = new DumpsterService();
    android::sp<android::IServiceManager> sm(android::defaultServiceManager());
    const android::status_t status =
        sm->addService(android::String16(DUMPSTER_SERVICE_NAME), dumpsterService, false /* allowIsolated */);
    if (status != android::OK) {
        ALOGE("Service not added: %d", static_cast<int>(status));
        exit(2);
    }

    std::thread signalWatchThread([dumpsterService] { watch_dump_signal(dumpsterService); });

    android::IPCThreadState::self()->joinThreadPool();

    dumpsterService = nullptr;
    raise(SIGUSR1);
    signalWatchThread.join();

    return EXIT_SUCCESS;
}
