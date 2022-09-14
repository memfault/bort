#include "structuredlog.h"

#include <binder/IServiceManager.h>
#include <com/memfault/bort/internal/ILogger.h>
#include <utils/SystemClock.h>

#define MEMFAULT_STRUCTURED_LOGD_SERVICE_NAME "memfault_structured"

// memfault::StructuredLogger implementation
namespace memfault {

struct Internals {
  std::mutex mLoggerMutex;
  android::sp<com::memfault::bort::internal::ILogger> mLogger;
};

StructuredLogger::StructuredLogger() : mInt(new Internals()) {}
StructuredLogger::~StructuredLogger() {}

uint64_t StructuredLogger::timestamp() {
  return android::elapsedRealtimeNano();
}

void StructuredLogger::log(long timestamp, const std::string &tag, const std::string &message) {
  std::unique_lock<std::mutex> lock(mInt->mLoggerMutex);
  ensureServiceLocked();
  if (mInt->mLogger != nullptr) {
    mInt->mLogger->log(timestamp, android::String16(tag.c_str()), android::String16(message.c_str()));
  }
}


void StructuredLogger::log(const std::string &tag, const std::string &message) {
  log(timestamp(), tag, message);
}


void StructuredLogger::ensureServiceLocked() {
  android::sp<android::IServiceManager> sm = android::defaultServiceManager();
  android::sp<android::IBinder> binder = sm->getService(android::String16(MEMFAULT_STRUCTURED_LOGD_SERVICE_NAME));
  if (binder != nullptr) {
    mInt->mLogger = android::interface_cast<com::memfault::bort::internal::ILogger>(binder);
  }
}

void StructuredLogger::finishReport(const std::string &json) {
  std::unique_lock<std::mutex> lock(mInt->mLoggerMutex);
  ensureServiceLocked();
  if (mInt->mLogger != nullptr) {
    mInt->mLogger->finishReport(android::String16(json.c_str()));
  }
}

void StructuredLogger::addValue(const std::string &json) {
  std::unique_lock<std::mutex> lock(mInt->mLoggerMutex);
  ensureServiceLocked();
  if (mInt->mLogger != nullptr) {
    mInt->mLogger->addValue(android::String16(json.c_str()));
  }
}

}


// Wrappers for c interoperability
structured_logger_t structured_log_new() {
  return new memfault::StructuredLogger();
}

void structured_log_destroy(structured_logger_t self) {
  auto *l = reinterpret_cast<memfault::StructuredLogger*>(self);
  delete l;
}

void structured_log_with_ts(structured_logger_t self, uint64_t timestamp,
    const char* tag, const char* message) {
  auto *l = reinterpret_cast<memfault::StructuredLogger*>(self);
  l->log(timestamp, std::string(tag), std::string(message));
}

void structured_log(structured_logger_t self, const char* tag, const char* message) {
  auto *l = reinterpret_cast<memfault::StructuredLogger*>(self);
  l->log(std::string(tag), std::string(message));
}

uint64_t structured_log_timestamp(structured_logger_t self) {
  auto *l = reinterpret_cast<memfault::StructuredLogger*>(self);
  return l->timestamp();
}

void structured_log_finish_report(structured_logger_t self, const char* json) {
  auto *l = reinterpret_cast<memfault::StructuredLogger*>(self);
  l->finishReport(std::string(json));
}

void structured_log(structured_logger_t self, const char* json) {
  auto *l = reinterpret_cast<memfault::StructuredLogger*>(self);
  l->addValue(std::string(json));
}
