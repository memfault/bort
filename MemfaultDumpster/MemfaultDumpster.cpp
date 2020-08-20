#define LOG_TAG "MemfaultDumpster"

#include <android-base/file.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include <DumpstateUtil.h>
#include <log/log_main.h>
#include <com/memfault/dumpster/BnDumpster.h>
#include <com/memfault/dumpster/IDumpsterBasicCommandListener.h>

#include <stdbool.h>
#include <stdlib.h>

#include <memory>
#include <string>
#include <vector>

#include "android-9/file.h"

#define DUMPSTER_SERVICE_NAME "memfault_dumpster"

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
              listener->onFinished(rv, std::make_unique<android::String16>(output.c_str()));
            }
            return android::binder::Status::ok();
        }

        std::vector<std::string> commandForId(int cmdId) {
            switch (cmdId) {
                case IDumpster::CMD_ID_GETPROP: return { "/system/bin/getprop" };
                default: return {};
            }
        }
    };
} // namespace

int main(void) {
    ALOGI("Starting...");

    signal(SIGPIPE, SIG_IGN);
    android::sp<android::ProcessState> ps(android::ProcessState::self());
    ps->setThreadPoolMaxThreadCount(2);
    ps->startThreadPool();
    ps->giveThreadPoolName();

    android::sp<DumpsterService> dumpsterService = new DumpsterService();

    android::sp<android::IServiceManager> sm(android::defaultServiceManager());
    const android::status_t status =
        sm->addService(android::String16(DUMPSTER_SERVICE_NAME), dumpsterService, false /* allowIsolated */);
    if (status != android::OK) {
        ALOGE("Service not added: %d", static_cast<int>(status));
        exit(2);
    }

    android::IPCThreadState::self()->joinThreadPool();
    return EXIT_SUCCESS;
}
