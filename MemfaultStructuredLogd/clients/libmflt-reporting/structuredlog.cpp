#include "structuredlog.h"

#include <binder/IServiceManager.h>
#include <com/memfault/bort/internal/ILogger.h>

#define MEMFAULT_STRUCTURED_LOGD_SERVICE_NAME "memfault_structured"

// memfault::StructuredLogger implementation
namespace memfault {

struct Internals {
  std::mutex mLoggerMutex;
  android::sp<com::memfault::bort::internal::ILogger> mLogger;
};

StructuredLogger::StructuredLogger() : mInt(new Internals()) {}
StructuredLogger::~StructuredLogger() {}

void StructuredLogger::ensureServiceLocked() {
  android::sp<android::IServiceManager> sm = android::defaultServiceManager();
  android::sp<android::IBinder> binder =
    sm->getService(android::String16(MEMFAULT_STRUCTURED_LOGD_SERVICE_NAME));
  if (binder != nullptr) {
    mInt->mLogger = android::interface_cast<com::memfault::bort::internal::ILogger>(binder);
  }
}

void StructuredLogger::addValue(const std::string &json) {
  std::unique_lock<std::mutex> lock(mInt->mLoggerMutex);
  ensureServiceLocked();
  if (mInt->mLogger != nullptr) {
    mInt->mLogger->addValue(android::String16(json.c_str()));
  }
}

}  // namespace memfault
