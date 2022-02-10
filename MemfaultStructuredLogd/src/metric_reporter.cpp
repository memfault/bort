#include <string>
#include <unordered_map>
#include "metric_reporter.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"
#include "log.h"

using namespace rapidjson;

namespace structured {

static constexpr int kReportVersion = 1;
static constexpr char kVersion[] = "version";
static constexpr char kStartTimestampMs[] = "startTimestampMs";
static constexpr char kEndTimestampMs[] = "endTimestampMs";
static constexpr char kReportType[] = "reportType";
static constexpr char kMetrics[] = "metrics";
static constexpr char kInternalMetrics[] = "internalMetrics";

template<class T>
T stringAsValue(const std::string &stringValue) {
    T result;
    std::istringstream iss(stringValue);
    iss >> result;
    return result;
}

static void writeMetrics(Writer<StringBuffer> &reportWriter,
                         const std::vector<std::tuple<std::string, bool, std::string, MetricValueType>> &metrics,
                         bool writeInternal) {
    size_t n = 0;
    StringBuffer metricGroup;
    Writer<StringBuffer> metricGroupWriter(metricGroup);

    metricGroupWriter.StartObject();
    for (auto &metric : metrics) {
        std::string name;
        bool isInternal;
        std::string value;
        MetricValueType valueType;

        std::tie(name, isInternal, value, valueType) = metric;

        if (isInternal != writeInternal) continue;

        n++;

        metricGroupWriter.Key(name.c_str());
        switch (valueType) {
            case Uint64:
                metricGroupWriter.Uint64(stringAsValue<uint64_t>(value));
                break;
            case Int64:
                metricGroupWriter.Int64(stringAsValue<int64_t>(value));
                break;
            case Double:
                metricGroupWriter.Double(stringAsValue<double>(value));
                break;
            case String:
                [[clang::fallthrough]];
            default:
                metricGroupWriter.String(value.c_str());
        }
    }
    metricGroupWriter.EndObject();

    // We always have a metrics object, even if empty but internal metrics will often be empty so omit that.
    if (n || !writeInternal) {
        reportWriter.Key(writeInternal ? kInternalMetrics : kMetrics);
        reportWriter.RawValue(metricGroup.GetString(), metricGroup.GetSize(), rapidjson::kObjectType);
    }
}

static const std::string formatReport(const Report &report) {
    StringBuffer buf;
    Writer<StringBuffer> writer(buf);
    writer.StartObject();

    writer.Key(kVersion);
    writer.Uint(kReportVersion);

    writer.Key(kStartTimestampMs);
    writer.Uint64(report.startTimestamp);

    writer.Key(kEndTimestampMs);
    writer.Uint64(report.finishTimestamp);

    writer.Key(kReportType);
    writer.String(report.type.c_str());

    writeMetrics(writer, report.metrics, false /* writeInternal */);
    writeMetrics(writer, report.metrics, true /* writeInternal */);

    writer.EndObject();

    return buf.GetString();
}

void StoredReporter::finishReport(uint8_t version, const std::string &type, int64_t timestamp, bool startNextReport) {
    auto report = storage->finishReport(version, type, timestamp, startNextReport);
    if (report != nullptr) {
        handleReport(*report, formatReport(*report));
    }
}

void StoredReporter::addValue(uint8_t version, const std::string &type, int64_t timestamp, const std::string &eventName,
                              bool internal, const std::vector<std::string> &aggregationTypes, const std::string &value,
                              MetricValueType valueType) {
    storage->storeMetricValue(
            version,
            type,
            timestamp,
            eventName,
            internal,
            parseAggregationTypes(aggregationTypes),
            value,
            valueType
    );
}

uint64_t StoredReporter::parseAggregationTypes(const std::vector<std::string> &aggregationTypes) {
    static const std::unordered_map<std::string, MetricAggregationType> aggregationTypeMappings {
            { "MIN", NUMERIC_MIN },
            { "MAX", NUMERIC_MAX },
            { "SUM", NUMERIC_SUM },
            { "MEAN", NUMERIC_MEAN },
            { "COUNT", NUMERIC_COUNT },
            { "TIME_TOTALS", STATE_TIME_TOTALS },
            { "TIME_PER_HOUR", STATE_TIME_PER_HOUR },
            { "LATEST_VALUE", STATE_LATEST_VALUE },
    };

    uint64_t result = 0;
    for (auto &it : aggregationTypes) {
        auto mappedType = aggregationTypeMappings.find(it);
        if (mappedType != aggregationTypeMappings.end()) {
            result |= mappedType->second;
        } else {
            if (spammyLogRateLimiter->take(1)) {
                ALOGW("Invalid metric aggregation type: %s", it.c_str());
            }
        }
    }
    return result;
}

}
