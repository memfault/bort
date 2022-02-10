#pragma once

#include <utility>
#include <vector>
#include <map>

namespace structured {

enum MetricAggregationType {
    NUMERIC_MIN = 1 << 0,
    NUMERIC_MAX = 1 << 1,
    NUMERIC_SUM = 1 << 2,
    NUMERIC_MEAN = 1 << 3,
    NUMERIC_COUNT = 1 << 4,
    STATE_TIME_TOTALS = 1 << 5,
    STATE_TIME_PER_HOUR = 1 << 6,
    STATE_LATEST_VALUE = 1 << 7
};

enum MetricValueType {
    Uint64,
    Int64,
    Double,
    String,
};

class Report {
public:
    Report(
            uint8_t version,
            std::string type,
            int64_t startTimestamp,
            int64_t finishTimestamp
    ) : version(version), type(std::move(type)), startTimestamp(startTimestamp), finishTimestamp(finishTimestamp) {}

    void addMetric(const std::string &name, bool internal, const std::string &value, MetricValueType valueType) {
        metrics.emplace_back(std::make_tuple(name, internal, value, valueType));
    }

    std::vector<std::tuple<std::string, bool, std::string, MetricValueType>> metrics;
    uint8_t version;
    const std::string type;
    int64_t startTimestamp;
    int64_t finishTimestamp;
};

// Suffixes for aggregation data
#define MIN_SUFFIX ".min"
#define MAX_SUFFIX ".max"
#define SUM_SUFFIX ".sum"
#define MEAN_SUFFIX ".mean"
#define COUNT_SUFFIX ".count"
#define TIME_TOTALS_SECS ".total_secs"
#define TIME_PER_HOUR_SECS_HOUR ".secs/hour"
#define LATEST_VALUE_SUFFIX ".latest"

}
