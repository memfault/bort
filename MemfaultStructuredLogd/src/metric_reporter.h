#pragma once

#include <utility>
#include <functional>

#include "metrics.h"
#include "storage.h"
#include "rate_limiter.h"

namespace structured {

#define STRUCTURED_HD_METRIC_DUMP_PATH_DEFAULT "/data/system/MemfaultStructuredLogd/hd_report.json"

typedef std::function<void(const Report &, const std::string &reportJson, const std::string *hdReportPath)> ReportHandler;

class Reporter {
public:
    virtual ~Reporter() {};
    virtual void finishReport(uint8_t version, const std::string &type, int64_t timestamp, bool startNextReport) = 0;
    virtual void addValue(
            uint8_t version,
            const std::string &type,
            int64_t timestamp,
            const std::string &eventName,
            bool internal,
            uint64_t aggregationTypes,
            const std::string &value,
            MetricValueType valueType,
            const std::string &dataType,
            const std::string &metricType,
            bool carryOver
    ) = 0;
};

class StoredReporter : public Reporter {
public:
    explicit StoredReporter(
            std::shared_ptr<StorageBackend> &storage,
            std::shared_ptr<Config> &config,
            ReportHandler handleReport,
            std::unique_ptr<TokenBucketRateLimiter> spammyLogRateLimiter,
            std::string hdMetricDumpPath = STRUCTURED_HD_METRIC_DUMP_PATH_DEFAULT
    ) : storage(storage), config(config), handleReport(std::move(handleReport)),
        spammyLogRateLimiter(std::move(spammyLogRateLimiter)), hdMetricDumpPath(std::move(hdMetricDumpPath)) {}
    ~StoredReporter() override {}

    void finishReport(uint8_t version, const std::string &type, int64_t timestamp, bool startNextReport) override;
    void addValue(
            uint8_t version,
            const std::string &type,
            int64_t timestamp,
            const std::string &eventName,
            bool internal,
            uint64_t aggregationTypes,
            const std::string &value,
            MetricValueType valueType,
            const std::string &dataType,
            const std::string &metricType,
            bool carryOver
    ) override;
private:
    std::shared_ptr<StorageBackend> storage;
    std::shared_ptr<Config> config;
    const ReportHandler handleReport;
    std::unique_ptr<TokenBucketRateLimiter> spammyLogRateLimiter;
    const std::string hdMetricDumpPath;
};

}
