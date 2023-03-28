#pragma once

#include <memory>

#include <rapidjson/filewritestream.h>
#include <rapidjson/writer.h>
#include "storage.h"
#include "version.h"

// String constants for HighResTelemetryBody
static constexpr char kSchemaVersion[] = "schema_version";
static constexpr char kProducer[] = "producer";
static constexpr char kRollups[] = "rollups";
static constexpr char kData[] = "data";
static constexpr char kMetadata[] = "metadata";
static constexpr char kStart[] = "start_time";
static constexpr char kDurationMs[] = "duration_ms";
static constexpr char kReportType[] = "report_type";

// String constants for Producer
static constexpr char kVersion[] = "version";
static constexpr char kId[] = "id";
static constexpr char kStructuredLog[] = "structured_logd";

// String constants for Metadata
static constexpr char kStringKey[] = "string_key";
static constexpr char kMetricType[] = "metric_type";
static constexpr char kDataType[] = "data_type";
static constexpr char kInternal[] = "internal";

// String constants for Datum
static constexpr char kTimestamp[] = "t";
static constexpr char kValue[] = "value";

namespace structured {

class HdReportWriter {
public:
    HdReportWriter(const char* filename) : _filename(filename) {}

    void writePreamble(const ReportMetadata &metadata) {
        _file = fopen(_filename, "w");
        _fos = std::make_unique<rapidjson::FileWriteStream>(_file, _buffer, sizeof(_buffer));
        _writer = std::make_unique<rapidjson::Writer<rapidjson::FileWriteStream>>(*_fos);

        _writer->StartObject();

        _writer->Key(kSchemaVersion);
        _writer->Int(1);

        _writer->Key(kStart);
        _writer->Int64(metadata.startTimestamp);
        _writer->Key(kDurationMs);
        _writer->Int64(metadata.endTimestamp - metadata.startTimestamp);

        _writer->Key(kReportType);
        _writer->String(metadata.reportType.c_str());

        // producer
        _writer->Key(kProducer);
        _writer->StartObject();
        _writer->Key(kVersion);
        _writer->String(kStructuredLogdVersion);
        _writer->Key(kId);
        _writer->String(kStructuredLog);
        _writer->EndObject();

        // prepare for rollups
        _writer->Key(kRollups);
        _writer->StartArray();
    }

    void writeFooter() {
        _writer->EndArray();
        _writer->EndObject();
        _fos->Flush();
        fclose(_file);
    }

    void startRollup(const structured::MetricMetadata &metadata) {
        _writer->StartObject();
        _writer->Key(kMetadata);
        _writer->StartObject();
        _writer->Key(kStringKey);
        _writer->String(metadata.eventName.c_str());
        _writer->Key(kMetricType);
        _writer->String(metadata.metricType.c_str());
        _writer->Key(kDataType);
        _writer->String(metadata.dataType.c_str());
        _writer->Key(kInternal);
        _writer->Bool(metadata.internal);
        _writer->EndObject();
        _writer->Key(kData);
        _writer->StartArray();
    }

    void endRollup() {
        _writer->EndArray();
        _writer->EndObject();
    }

    void writeDatum(const std::string &dataType, const MetricDatum &datum) {
        _writer->StartObject();
        _writer->Key(kTimestamp);
        _writer->Int64(datum.timestamp);
        _writer->Key(kValue);
        if (dataType == "double") {
            _writer->Double(datum.numberValue);
        } else if (dataType == "boolean") {
            _writer->Bool(datum.booleanValue);
        } else {
            _writer->String(datum.strValue.c_str());
        }
        _writer->EndObject();
    }

private:
    const char *_filename;
    FILE* _file;
    char _buffer[4*1024];
    std::unique_ptr<rapidjson::FileWriteStream> _fos;
    std::unique_ptr<rapidjson::Writer<rapidjson::FileWriteStream>> _writer;
};
}
