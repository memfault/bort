#include <fstream>

#include "rapidjson/document.h"
#include "rapidjson/stringbuffer.h"

#include "logwriter.h"

namespace structured {

static constexpr int kCurrentSchemaVersion = 1;
static constexpr char kSchemaVersion[] = "schema_version";
static constexpr char kLinuxBootId[] = "linux_boot_id";
static constexpr char kCid[] = "cid";
static constexpr char kNextCid[] = "next_cid";
static constexpr char kEvents[] = "events";
static constexpr char kTimestamp[] = "ts";
static constexpr char kType[] = "type";
static constexpr char kInternalType[] = "_type";
static constexpr char kData[] = "data";
static constexpr char kOriginalType[] = "original_type";
static constexpr char kOriginalData[] = "original_data";
static constexpr char kInvalidData[] = "invalid_data";

JsonLogWriter::JsonLogWriter(std::ostream& output, const std::string& bootId, const std::string &cid,
                             const std::string &nextCid)
        : oStreamWrapper(output), writer(oStreamWrapper) {
    writer.StartObject();
    writer.Key(kSchemaVersion);
    writer.Int(kCurrentSchemaVersion);
    writer.Key(kLinuxBootId);
    writer.String(bootId.c_str());

    writer.Key(kCid);
    writer.String(cid.c_str());
    writer.Key(kNextCid);
    writer.String(nextCid.c_str());

    writer.Key(kEvents);
    writer.StartArray();
}

JsonLogWriter::~JsonLogWriter() {
    writer.EndArray();
    writer.EndObject();
}

void JsonLogWriter::write(const LogEntry &e) {
    writer.StartObject();
    writer.Key(kTimestamp);
    writer.Double(e.timestamp / 1000000.0);

    writeBlob(e);

    writer.EndObject();
}

static void writeInvalidBlobData(rapidjson::Writer<rapidjson::OStreamWrapper> &writer, const LogEntry &entry) {
    writer.StartObject();
    writer.Key(kOriginalType);
    writer.String(entry.type.c_str());
    writer.Key(kOriginalData);
    writer.String(entry.blob.c_str());
    writer.EndObject();
}

void JsonLogWriter::writeBlob(const LogEntry &entry) {
    auto data = entry.blob;

    rapidjson::Document document;
    document.Parse(entry.blob.c_str());

    if (document.HasParseError()) {
        writer.Key(kInternalType);
        writer.String(kInvalidData);

        writer.String(kData);
        writeInvalidBlobData(writer, entry);
    } else {
        writer.Key(entry.internal ? kInternalType : kType);
        writer.String(entry.type.c_str());

        writer.Key(kData);
        writer.RawValue(entry.blob.c_str(), entry.blob.size(), document.GetType());
    }
}

}
