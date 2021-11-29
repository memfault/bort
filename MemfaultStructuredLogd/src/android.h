#pragma once

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/PermissionCache.h>
#include <binder/ProcessState.h>
#include <utils/Errors.h>
#include "log.h"
#include "logger.h"
#include "metric_service.h"

#include "com/memfault/bort/internal/BnLogger.h"

#define STRUCTURED_SERVICE_NAME       "memfault_structured"
#define STRUCTURED_DROPBOX_TAG        "memfault_structured"
#define STRUCTURED_REPORT_DROPBOX_TAG "memfault_report"
#define STRUCTURED_DUMP_FILE          "/data/system/MemfaultStructuredLogd/dump.json"
#define STRUCTURED_REPORT_FILE        "/data/system/MemfaultStructuredLogd/report.json"

#define SPAMMY_METRIC_LOG_RATE_LIMITER_INITIAL_CAPACITY 1
#define SPAMMY_METRIC_LOG_RATE_BUCKET_SIZE 5
#define SPAMMY_METRIC_LOG_RATE_MS_PER_TOKEN 720000

static const android::String16 STRUCTURED_LOG_CONFIG_PERM("com.memfault.bort.permission.UPDATE_STRUCTURED_LOG_CONFIG");

namespace structured {

class LoggerService : public ::com::memfault::bort::internal::BnLogger {
public:
    LoggerService(
            std::unique_ptr<Logger> &logger,
            std::unique_ptr<MetricService> &metricService
    ) : logger(std::move(logger)), metricService(std::move(metricService)) {}

    ~LoggerService() {}

    android::binder::Status log(const int64_t timestamp,
                                const android::String16 &type,
                                const android::String16 &data) {
        _log(timestamp, type, data, false /* internal */);
        return android::binder::Status::ok();
    }

    android::binder::Status logInternal(const int64_t timestamp,
                                        const android::String16 &type,
                                        const android::String16 &data) {
        _log(timestamp, type, data, true /*internal */);
        return android::binder::Status::ok();
    }

    android::binder::Status triggerDump() {
        logger->triggerDump();
        return android::binder::Status::ok();
    }

    android::binder::Status reloadConfig(const android::String16 &config) {
        auto ipc = android::IPCThreadState::self();
        if (!android::PermissionCache::checkPermission(STRUCTURED_LOG_CONFIG_PERM,
                                                       ipc->getCallingPid(),
                                                       ipc->getCallingUid())) {
            return android::binder::Status::fromExceptionCode(
                    android::binder::Status::Exception::EX_SECURITY,
                    "Config reload denied, caller does not have the bort control permission"
            );
        }
        logger->reloadConfig(android::String8(config).string());
        return android::binder::Status::ok();
    }

    android::binder::Status finishReport(const android::String16 &json) {
        metricService->finishReport(android::String8(json).string());
        return android::binder::Status::ok();
    }

    android::binder::Status addValue(const android::String16 &json) {
        metricService->addValue(android::String8(json).string());
        return android::binder::Status::ok();
    }
private:
    std::unique_ptr<Logger> logger;
    std::unique_ptr<MetricService> metricService;

    void _log(const int64_t timestamp,
              const android::String16 &type,
              const android::String16 &data,
              bool internal) {
        logger->log(timestamp,
                    android::String8(type).string(),
                    android::String8(data).string(),
                    internal
                    );
    }
};

void createService(const char *storagePath);
}
