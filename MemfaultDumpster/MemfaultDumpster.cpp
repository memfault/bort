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
#include <stdlib.h>

#include <memory>
#include <string>
#include <vector>

#include "android-9/file.h"
#include "ContinuousLogcat.h"

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

  class DumpsterService : public BnDumpster {
        public:
        DumpsterService() : clog(new memfault::ContinuousLogcat()) {
        }

        android::binder::Status getVersion(int *_aidl_return) {
          *_aidl_return = IDumpster::VERSION;
          return android::binder::Status::ok();
        }

        android::binder::Status runBasicCommand(
            int cmdId, const android::sp<IDumpsterBasicCommandListener> &listener) override {
            std::vector<std::string> command = commandForId(cmdId);
            if (command.empty()) {
              listener->onUnsupported();
            } else {
              std::string output;
              const int rv = RunCommandToString(command, output);
#if PLATFORM_SDK_VERSION <= 30
              listener->onFinished(rv, std::make_unique<android::String16>(output.c_str()));
#else
              listener->onFinished(rv, android::String16(output.c_str()));
#endif
            }
            return android::binder::Status::ok();
        }

        std::vector<std::string> commandForId(int cmdId) {
            switch (cmdId) {
                case IDumpster::CMD_ID_GETPROP: return { "/system/bin/getprop" };
                case IDumpster::CMD_ID_GETPROP_TYPES: return { "/system/bin/getprop", "-T" };
                case IDumpster::CMD_ID_SET_BORT_ENABLED_PROPERTY_ENABLED: return {
                        "/system/bin/setprop", BORT_ENABLED_PROPERTY, "1"
                };
                case IDumpster::CMD_ID_SET_BORT_ENABLED_PROPERTY_DISABLED: return {
                        "/system/bin/setprop", BORT_ENABLED_PROPERTY, "0"
                };
                case IDumpster::CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_ENABLED: return {
                        "/system/bin/setprop", STRUCTURED_ENABLED_PROPERTY, "1"
                };
                case IDumpster::CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_DISABLED: return {
                        "/system/bin/setprop", STRUCTURED_ENABLED_PROPERTY, "0"
                };
                case IDumpster::CMD_ID_CYCLE_COUNT_NEVER_USE: return {
                        "echo", ""
                };
                case IDumpster::CMD_ID_PROC_STAT: return {
                        "cat", "/proc/stat"
                };

                default: return {};
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
              filter_specs.emplace_back(android::String8(it).string());
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
