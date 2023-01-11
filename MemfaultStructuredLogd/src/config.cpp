#include "config.h"

#include <cinttypes>

#include "rapidjson/document.h"
#include "log.h"

namespace structured {

static constexpr char kRateLimitDefaultCapacity[] = "default_capacity";
static constexpr char kRateLimitDefaultPeriod[] = "default_period_ms";
static constexpr char kRateLimitingSettings[] = "structured_log.rate_limiting_settings";
static constexpr char kDumpPeriodMs[] = "structured_log.dump_period_ms";
static constexpr char kNumEventsBeforeDump[] = "structured_log.num_events_before_dump";
static constexpr char kMaxMessageSizeBytes[] = "structured_log.max_message_size_bytes";
static constexpr char kMinStorageThresholdBytes[] = "structured_log.min_storage_threshold_bytes";
static constexpr char kMetricReportEnabled[] = "structured_log.metric_report_enabled";
static constexpr char kHighResMetricsEnabled[] = "structured_log.high_res_metrics_enabled";

template<typename T>
static T _get_config_num(rapidjson::Document &document, const std::string &key, T defaultValue) {
    if (!document.HasMember(key.c_str()) || !document[key.c_str()].IsNumber()) {
        return defaultValue;
    }
    return document[key.c_str()].Get<T>();
}

static bool _get_config_bool(rapidjson::Document &document, const std::string &key, bool defaultValue) {
    if (!document.HasMember(key.c_str()) || !document[key.c_str()].IsBool()) {
        return defaultValue;
    }
    return document[key.c_str()].GetBool();
}

static RateLimiterConfig
_get_config_rate_limit(rapidjson::Document &document, const std::string &key, const RateLimiterConfig &defaultValue) {
    if (!document.HasMember(key.c_str()) || !document[key.c_str()].IsObject()) {
        ALOGW("Config has no key %s, returning defaults", key.c_str());
        return defaultValue;
    }

    auto object = document[key.c_str()].GetObject();
    if (
            (object.HasMember(kRateLimitDefaultCapacity) && object[kRateLimitDefaultCapacity].IsNumber()) &&
            (object.HasMember(kRateLimitDefaultPeriod) && object[kRateLimitDefaultPeriod].IsNumber())
            ) {
        return RateLimiterConfig{
                .initialCapacity = object[kRateLimitDefaultCapacity].GetUint(),
                .capacity = object[kRateLimitDefaultCapacity].GetUint(),
                .msPerToken = object[kRateLimitDefaultPeriod].GetUint(),
        };
    }

    ALOGW("Rate limiting settings is malformed, returning defaults");
    return defaultValue;
}

void StoredConfig::_reloadLocked() {
    rapidjson::Document configDocument;
    configDocument.Parse(storage->getConfig().c_str());

    const RateLimiterConfig &defaultRateLimiterConfig = RateLimiterConfig{
            .initialCapacity = RATE_LIMIT_INITIAL_CAPACITY,
            .capacity = RATE_LIMIT_CAPACITY,
            .msPerToken = RATE_LIMIT_PERIOD_MS,
    };

    if (configDocument.HasParseError() || !configDocument.IsObject()) {
        ALOGW("Stored config cannot be parsed or is not an object, using defaults");
        this->rateLimiterConfig = defaultRateLimiterConfig;
        this->numEventsBeforeDump = NUM_EVENTS_BEFORE_DUMP;
        this->dumpPeriodMs = DUMP_PERIOD_MS;
        this->minStorageTreshold = MIN_STORAGE_THRESHOLD_BYTES;
        this->maxMessageSize = MAX_MESSAGE_SIZE_BYTES;
        this->metricReportEnabled = METRIC_REPORTS_ENABLED;
        this->highResMetricsEnabled = HIGH_RES_METRICS_ENABLED;
    } else {
        ALOGV("Loading config from storage");
        this->rateLimiterConfig = _get_config_rate_limit(configDocument, kRateLimitingSettings,
                                                         defaultRateLimiterConfig);
        this->dumpPeriodMs = _get_config_num(configDocument, kDumpPeriodMs, uint64_t(DUMP_PERIOD_MS));
        this->numEventsBeforeDump = _get_config_num(configDocument, kNumEventsBeforeDump,
                                                    NUM_EVENTS_BEFORE_DUMP);
        this->minStorageTreshold = _get_config_num(configDocument, kMinStorageThresholdBytes,
                                              uint64_t(MIN_STORAGE_THRESHOLD_BYTES));
        this->maxMessageSize = _get_config_num(configDocument, kMaxMessageSizeBytes,
                                               MAX_MESSAGE_SIZE_BYTES);
        this->metricReportEnabled = _get_config_bool(configDocument, kMetricReportEnabled,
                                               METRIC_REPORTS_ENABLED);
        this->highResMetricsEnabled = _get_config_bool(configDocument, kHighResMetricsEnabled,
                                               HIGH_RES_METRICS_ENABLED);
    }
}

StoredConfig::StoredConfig(std::shared_ptr<StorageBackend> &backend) : storage(backend) {
    _reloadLocked();
}

void StoredConfig::updateConfig(const std::string &config) {
    std::unique_lock<std::mutex> lock(mutex);
    storage->setConfig(config);
    _reloadLocked();
}

RateLimiterConfig StoredConfig::getRateLimiterConfig() {
    std::unique_lock<std::mutex> lock(mutex);
    return rateLimiterConfig;
}

uint64_t StoredConfig::getDumpPeriodMs() {
    std::unique_lock<std::mutex> lock(mutex);
    return dumpPeriodMs;
}

uint32_t StoredConfig::getNumEventsBeforeDump() {
    std::unique_lock<std::mutex> lock(mutex);
#ifdef BORT_UNDER_TEST
    // When in CI we should not update with dynamic settings as the tests expect a specific (short) number
    // of events before dumping.
    return 50;
#else
    return numEventsBeforeDump;
#endif
}

uint32_t StoredConfig::getMaxMessageSize() {
    std::unique_lock<std::mutex> lock(mutex);
    return maxMessageSize;
}

uint64_t StoredConfig::getMinStorageThreshold() {
    std::unique_lock<std::mutex> lock(mutex);
    return minStorageTreshold;
}

bool StoredConfig::isMetricReportEnabled() {
    std::unique_lock<std::mutex> lock(mutex);
#ifdef BORT_UNDER_TEST
    // Always enable for bort testing
    return true;
#else
    return metricReportEnabled;
#endif
}

bool StoredConfig::isHighResMetricsEnabled() {
    std::unique_lock<std::mutex> lock(mutex);
    return highResMetricsEnabled;
}

}
