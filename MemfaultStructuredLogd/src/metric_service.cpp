#include <memory>
#include "metric_service.h"
#include "log.h"
#include <rapidjson/document.h>

using namespace rapidjson;

namespace structured {

static constexpr char kVersion[] = "version";
static constexpr char kTimestampMs[] = "timestampMs";
static constexpr char kReportType[] = "reportType";
static constexpr char kStartNextReport[] = "startNextReport";
static constexpr char kEventName[] = "eventName";
static constexpr char kInternal[] = "internal";
static constexpr char kAggregations[] = "aggregations";
static constexpr char kValue[] = "value";

/**
 * Checks whether the schema for finish is compliant with v1. The document is
 * expected to be returned from #parseJsonAsDocument, which ensures it is an object
 * type and contains a version field with an integer on it.
 */
static bool isFinishCompliantV1(const std::unique_ptr<Document> &doc) {
    auto root = doc->GetObject();

    if (!root.HasMember(kTimestampMs) || !root[kTimestampMs].IsUint64()) {
        ALOGE("Expected schema to have a 'timestampMs' numeric field but it doesn't");
        return false;
    }
    if (!root.HasMember(kReportType) || !root[kReportType].IsString()) {
        ALOGE("Expected schema to have a 'reportType' string field but it doesn't");
        return false;
    }
    if (!root.HasMember(kReportType) || !root[kReportType].IsString()) {
        ALOGE("Expected schema to have a 'reportType' string field but it doesn't");
        return false;
    }

    if (root.HasMember(kStartNextReport) && !root[kStartNextReport].IsBool()) {
        ALOGE("'%s' is an optional field but if present must be a boolean.", kStartNextReport);
        return false;
    }

    return true;
}

/**
 * Checks whether the schema for addValue is compliant with v1. The document is
 * expected to be returned from #parseJsonAsDocument, which ensures it is an object
 * type and contains a version field with an integer on it.
 */
static bool isAddValueCompliantV1(const std::unique_ptr<Document> &doc) {
    auto root = doc->GetObject();

    if (!root.HasMember(kTimestampMs) || !root[kTimestampMs].IsUint64()) {
        ALOGE("Expected addValue schema to have a 'timestampMs' numeric field but it doesn't");
        return false;
    }
    if (!root.HasMember(kReportType) || !root[kReportType].IsString()) {
        ALOGE("Expected addValue schema to have a 'reportType' string field but it doesn't");
        return false;
    }
    if (!root.HasMember(kEventName) || !root[kEventName].IsString()) {
        ALOGE("Expected addValue schema to have a 'eventName' string field but it doesn't");
        return false;
    }
    if (root.HasMember(kInternal) && !root[kInternal].IsBool()) {
        ALOGE("Expected 'internal' to be missing (defaulting to false) or a boolean");
        return false;
    }
    if (!root.HasMember(kAggregations) || !root[kAggregations].IsArray()) {
        ALOGE("Expected addValue schema to have a 'aggreagtions' string array field but it doesn't");
        return false;
    }
    if (!root.HasMember(kValue) || (!root[kValue].IsString() && !root[kValue].IsNumber())) {
        ALOGE("Expected addValue schema to have a 'value' numeric/string field but it doesn't");
        return false;
    }

    auto aggregations = root[kAggregations].GetArray();
    for (SizeType i = 0; i < aggregations.Size(); i++) {
        if (!aggregations[i].IsString()) {
            ALOGE("Expected addValue schema to have only string aggregations but one of them is not a string");
            return false;
        }
    }

    return true;
}

static std::pair<uint8_t, std::unique_ptr<Document>> parseJsonAsDocument(const std::string &json) {
    auto doc = std::make_unique<Document>();
    doc->Parse(json.c_str());

    if (doc->HasParseError() || !doc->IsObject()) {
        ALOGE("Malformed json ignored when handling a metric report request");
        return std::make_pair(0, nullptr);
    }

    auto root = doc->GetObject();
    if (!root.HasMember(kVersion) || !root[kVersion].IsNumber()) {
        ALOGE("Expected incoming json to have a numeric 'version' field but none is present");
        return std::make_pair(0, nullptr);
    }

    uint8_t version = root[kVersion].GetInt();
    return std::make_pair(version, std::move(doc));
}

template <class T>
static std::string asString(const T &value) {
    std::stringstream strstream;
    strstream << value;
    return strstream.str();
}

static std::string valueAsString(const Value &value) {
    if (value.IsString()) return value.GetString();
    else {
        // This looks convoluted but double mantissa is 52-bit so we would lose precision
        // on a uint64 so we try to type cast those first and fallback to double.
        if (value.IsUint64()) {
            return asString(value.GetUint64());
        } else if (value.IsInt64()) {
            return asString(value.GetInt64());
        } else {
            return asString(value.GetDouble());
        }
    }
}

void MetricService::finishReport(const std::string &json) {
    if (!config->isMetricReportEnabled()) return;

    auto parsed = parseJsonAsDocument(json);
    auto version = parsed.first;
    auto document = std::move(parsed.second);
    if (document == nullptr) return;

    if (isFinishCompliantV1(document)) {
        auto root = document->GetObject();
        auto startNextReport = root.HasMember(kStartNextReport) && root[kStartNextReport].GetBool();
        reporter->finishReport(
                version,
                root[kReportType].GetString(),
                root[kTimestampMs].GetUint64(),
                startNextReport
        );
    } else {
        ALOGE("Expected finishReport schema to be compliant with v1: { timestampMs: long, reportType: str } but it isn't'");
    }
}

static MetricValueType getMetricValueType(const Value &value) {
    if (value.IsUint64()) return Uint64;
    if (value.IsInt64()) return Int64;
    if (value.IsDouble()) return Double;
    return String;
}

void MetricService::addValue(const std::string &json) {
    if (!config->isMetricReportEnabled()) return;

    auto parsed = parseJsonAsDocument(json);
    auto version = parsed.first;
    auto document = std::move(parsed.second);
    if (document == nullptr) return;

    if (isAddValueCompliantV1(document)) {
        auto root = document->GetObject();
        auto aggregations = root[kAggregations].GetArray();
        std::vector<std::string> aggregationsVec;
        for (SizeType i = 0 ;i < aggregations.Size(); i++) {
            auto aggregation = aggregations[i].GetString();
            aggregationsVec.emplace_back(std::string(aggregation));
        }

        reporter->addValue(
                version,
                root[kReportType].GetString(),
                root[kTimestampMs].GetUint64(),
                root[kEventName].GetString(),
                root.HasMember(kInternal) && root[kInternal].GetBool(),
                aggregationsVec,
                valueAsString(root[kValue]),
                getMetricValueType(root[kValue])
        );
    } else {
        ALOGE("Expected addValue schema to be compliant with v1: { timestampMs: long, reportType: str, "
              "eventName: str, aggregations: array<str>, value: any } but it isn't'");
    }
}

}
