#pragma once

#include <utility>
#include <functional>

#include "metrics.h"
#include "storage.h"
#include "rate_limiter.h"

namespace structured {

typedef std::function<void(const Report &, const std::string &reportJson)> ReportHandler;

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
            const std::vector<std::string> &aggregationTypes,
            const std::string &value,
            MetricValueType valueType
    ) = 0;
};

class StoredReporter : public Reporter {
public:
    explicit StoredReporter(
            std::shared_ptr<StorageBackend> &storage,
            ReportHandler handleReport,
            std::unique_ptr<TokenBucketRateLimiter> spammyLogRateLimiter
    ) : storage(storage), handleReport(std::move(handleReport)), spammyLogRateLimiter(std::move(spammyLogRateLimiter)) {}
    ~StoredReporter() override {}

    void finishReport(uint8_t version, const std::string &type, int64_t timestamp, bool startNextReport) override;
    void addValue(
            uint8_t version,
            const std::string &type,
            int64_t timestamp,
            const std::string &eventName,
            bool internal,
            const std::vector<std::string> &aggregationTypes,
            const std::string &value,
            MetricValueType valueType
    ) override;
private:
    std::shared_ptr<StorageBackend> storage;
    const ReportHandler handleReport;
    std::unique_ptr<TokenBucketRateLimiter> spammyLogRateLimiter;

    uint64_t parseAggregationTypes(const std::vector<std::string> &vector);
};

}
