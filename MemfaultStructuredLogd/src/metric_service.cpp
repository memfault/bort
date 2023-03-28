#include <memory>
#include "metric_service.h"
#include "log.h"
#include <rapidjson/document.h>
#include <unordered_map>

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
static constexpr char kMetricType[] = "metricType";
static constexpr char kDataType[] = "dataType";
static constexpr char kCarryOver[] = "carryOver";

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
    if (!root.HasMember(kVersion) || !root[kVersion].IsUint()) {
        ALOGE("Expected schema to have a 'version' numeric field but it doesn't");
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
static bool isAddValueCompliantV1(const rapidjson::Document::Object &root) {
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
    if (!root.HasMember(kValue) || (!root[kValue].IsString() && !root[kValue].IsNumber()
        && !root[kValue].IsBool())) {
        ALOGE("Expected addValue schema to have a 'value' numeric/string/bool field but it doesn't");
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

/**
 * Checks whether the schema for addValue is compliant with v2. The document is
 * expected to be returned from #parseJsonAsDocument, which ensures it is an object
 * type and contains a version field with an integer on it. V2 is a V1-compatible
 * document, with a version of at least 2 with dataType,metricType and carryOver fields.
 */
static bool isAddValueCompliantV2(const rapidjson::Document::Object &root) {
    if (!isAddValueCompliantV1(root)) {
        ALOGE("Expected addValue V2 to be compliant with V1, but it isn't");
        return false;
    }

    if (!root.HasMember(kVersion) || !root[kVersion].IsNumber() || root[kVersion].GetInt() < 2) {
        ALOGE("Expected incoming json to have a numeric 'version' field of at least 2, but it does not");
        return false;
    }

    if (!root.HasMember(kDataType) || !root[kDataType].IsString()) {
        ALOGE("Expected addValue schema to have a 'dataType' string field but it doesn't");
        return false;
    }

    if (!root.HasMember(kMetricType) || !root[kMetricType].IsString()) {
        ALOGE("Expected addValue schema to have a 'metricType' string field but it doesn't");
        return false;
    }

    if (!root.HasMember(kCarryOver) || !root[kCarryOver].IsBool()) {
        ALOGE("Expected addValue schema to have a 'carryOver' boolean field but it doesn't");
        return false;
    }

    return true;
}

static std::unique_ptr<Document> parseJsonAsDocument(const std::string &json) {
    auto doc = std::make_unique<Document>();
    doc->Parse(json.c_str());
    if (doc->HasParseError()) {
        ALOGE("Malformed json ignored when handling a metric report request");
        return nullptr;
    }
    return doc;
}

static std::pair<uint8_t, bool> parseJsonEntry(const rapidjson::Document::Object &object) {
    if (!object.HasMember(kVersion) || !object[kVersion].IsNumber()) {
        ALOGE("Expected incoming json to have a numeric 'version' field but none is present");
        return std::make_pair(0, false);
    }

    uint8_t version = object[kVersion].GetInt();
    return std::make_pair(version, true);
}

static std::pair<uint8_t, std::unique_ptr<Document>> parseJsonAsDocumentWithSingleEntry(const std::string &json) {
    auto document = parseJsonAsDocument(json);
    if (document == nullptr) {
        return std::make_pair(0, nullptr);
    }

    if (!document->IsObject()) {
        ALOGE("Expected incoming json to be an object but it is not");
        return std::make_pair(0, nullptr);
    }

    uint8_t version;
    bool valid;
    std::tie(version, valid) = parseJsonEntry(document->GetObject());

    if (!valid) {
        return std::make_pair(0, nullptr);
    }

    return std::make_pair(version, std::move(document));
}

template <class T>
static std::string asString(const T &value) {
    std::stringstream strstream;
    strstream << value;
    return strstream.str();
}

static std::string valueAsString(const Value &value) {
    if (value.IsString()) return value.GetString();
    else if (value.IsBool()) return value.GetBool() ? "1" : "0";
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

    auto parsed = parseJsonAsDocumentWithSingleEntry(json);
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

static std::string metricTypeToString(MetricValueType type) {
    switch (type) {
        case Uint64:
        case Int64:
        case Double:
            return "double";
        case String:
            return "string";
    }
}

static uint64_t parseAggregationTypes(const GenericArray<false, GenericValue<UTF8<>>::ValueType>& aggregationTypes) {
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
        auto mappedType = aggregationTypeMappings.find(it.GetString());
        if (mappedType != aggregationTypeMappings.end()) {
            result |= mappedType->second;
        }
    }
    return result;
}

static std::string guessDataTypeFromAggregations(uint64_t aggregationTypes) {
    if (aggregationTypes & NUMERIC_COUNT) {
        return "counter";
    }

    if (aggregationTypes & (NUMERIC_MEAN | NUMERIC_MAX | NUMERIC_SUM)) {
        return "gauge";
    }

    return "property";
}

void MetricService::addValueFromObject(const rapidjson::Document::Object &object) {
    uint8_t version;
    bool valid;
    std::tie(version, valid) = parseJsonEntry(object);

    if (!valid) {
        return;
    }

    if (version >= 2 && isAddValueCompliantV2(object)) {
        uint64_t aggregationTypes = parseAggregationTypes(object[kAggregations].GetArray());

        reporter->addValue(
                version,
                object[kReportType].GetString(),
                object[kTimestampMs].GetUint64(),
                object[kEventName].GetString(),
                object.HasMember(kInternal) && object[kInternal].GetBool(),
                aggregationTypes,
                valueAsString(object[kValue]),
                getMetricValueType(object[kValue]),
                object[kDataType].GetString(),
                object[kMetricType].GetString(),
                object[kCarryOver].GetBool()
        );
    } else if (version == 1 && isAddValueCompliantV1(object)) {
        uint64_t aggregationTypes = parseAggregationTypes(object[kAggregations].GetArray());
        reporter->addValue(
                version,
                object[kReportType].GetString(),
                object[kTimestampMs].GetUint64(),
                object[kEventName].GetString(),
                object.HasMember(kInternal) && object[kInternal].GetBool(),
                aggregationTypes,
                valueAsString(object[kValue]),
                getMetricValueType(object[kValue]),
                metricTypeToString(getMetricValueType(object[kValue])),
                guessDataTypeFromAggregations(aggregationTypes),
                false /* carryOver */
        );
    } else {
        ALOGE("Expected addValue schema to be compliant with v1: { timestampMs: long, reportType: str, "
              "eventName: str, aggregations: array<str>, value: any } or v2: { ...v1, carryOver: bool, "
              "dataType: str, metricType: str } but it isn't'");
    }
}

void MetricService::addValue(const std::string &json) {
    if (!config->isMetricReportEnabled()) return;

    auto document = parseJsonAsDocument(json);
    if (document == nullptr) {
        return;
    }

    if (document->IsObject()) {
        addValueFromObject(document->GetObject());
    } else if (document->IsArray()) {
        for (auto &it : document->GetArray()) {
            if (it.IsObject()) {
                addValueFromObject(it.GetObject());
            }
        }
    }
}

}
