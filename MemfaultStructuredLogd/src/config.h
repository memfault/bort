#pragma once

#include <memory>
#include <mutex>
#include <cstdint>

#include "storage.h"

// Defaults used in case the config is not available
#define RATE_LIMIT_INITIAL_CAPACITY 1000
#define RATE_LIMIT_CAPACITY 1000
#define RATE_LIMIT_PERIOD_MS (15 * 1000)
#define MAX_MESSAGE_SIZE_BYTES 4096
#define MIN_STORAGE_THRESHOLD_BYTES 268435456u
#define NUM_EVENTS_BEFORE_DUMP 1000
#define DUMP_PERIOD_MS (2 * 60 * 60 * 1000)
#define METRIC_REPORTS_ENABLED true
#define HIGH_RES_METRICS_ENABLED false

namespace structured {

struct RateLimiterConfig {
    uint32_t initialCapacity;
    uint32_t capacity;
    uint64_t msPerToken;
};

class Config {
public:
    virtual ~Config() {};
    virtual void updateConfig(const std::string &config) = 0;
    virtual RateLimiterConfig getRateLimiterConfig() = 0;
    virtual uint64_t getDumpPeriodMs() = 0;
    virtual uint32_t getNumEventsBeforeDump() = 0;
    virtual uint32_t getMaxMessageSize() = 0;
    virtual uint64_t getMinStorageThreshold() = 0;
    virtual bool isMetricReportEnabled() = 0;
    virtual bool isHighResMetricsEnabled() = 0;
    typedef std::shared_ptr<Config> SharedPtr;
};

class StoredConfig : public Config {
public:
    explicit StoredConfig(std::shared_ptr<StorageBackend> &backend);
    ~StoredConfig() override {};
    void updateConfig(const std::string &config) override;
    RateLimiterConfig getRateLimiterConfig() override;
    uint64_t getDumpPeriodMs() override;
    uint32_t getNumEventsBeforeDump() override;
    uint32_t getMaxMessageSize() override;
    uint64_t getMinStorageThreshold() override;
    bool isMetricReportEnabled() override;
    bool isHighResMetricsEnabled() override;
private:
    std::mutex mutex{};
    std::shared_ptr<StorageBackend> storage;
    RateLimiterConfig rateLimiterConfig;
    uint64_t dumpPeriodMs;
    uint32_t numEventsBeforeDump;
    uint32_t maxMessageSize;
    uint64_t minStorageTreshold;
    bool metricReportEnabled;
    bool highResMetricsEnabled;

    void _reloadLocked();
};

}
