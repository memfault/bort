#pragma once

#include "log.h"
#include <atomic>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>
#include "dumper.h"
#include "storage.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"

#include "com/memfault/bort/internal/BnLogger.h"

#define STRUCTURED_SERVICE_NAME "memfault_structured"
#define STRUCTURED_DROPBOX_TAG  "memfault_structured"
#define STRUCTURED_DUMP_FILE    "/data/system/MemfaultStructuredLogd/dump.json"

#define RATE_LIMIT_INITIAL_CAPACITY_PROPERTY "vendor.memfault.structured.initial_capacity"
#define RATE_LIMIT_CAPACITY_PROPERTY "vendor.memfault.structured.capacity"
#define RATE_LIMIT_PERIOD_MS_PROPERTY "vendor.memfault.structured.period_ms"
#define MAX_MESSAGE_SIZE_BYTES_PROPERTY "vendor.memfault.structured.max_message_size_bytes"
#define NUM_EVENTS_BEFORE_DUMP_PROPERTY "vendor.memfault.structured.num_events_before_dump"

// Defaults used in case the property-based settings are not available
#define RATE_LIMIT_INITIAL_CAPACITY 1000
#define RATE_LIMIT_CAPACITY 1000
#define RATE_LIMIT_PERIOD_MS (1 * 60 * 60 * 1000)
#define MAX_MESSAGE_SIZE_BYTES 4096
#define NUM_EVENTS_BEFORE_DUMP 1000

static constexpr char kOriginalType[] = "original_type";
static constexpr char kOversizedData[] = "oversized_data";
static constexpr char kDataSize[] = "size";

namespace structured {

class LoggerImpl : public ::com::memfault::bort::internal::BnLogger {
public:
    LoggerImpl(
            std::shared_ptr<StorageBackend> &storage,
            std::shared_ptr<Dumper> &dumper,
            size_t maxMessageSize,
            size_t numEventsBeforeDump
            ) : storage(storage), dumper(dumper), maxMessageSize(maxMessageSize),
                numEventsBeforeDump(numEventsBeforeDump), counter(0u) {}

    ~LoggerImpl() {}

    android::binder::Status log(const int64_t timestamp,
                                const android::String16 &type,
                                const android::String16 &data) {
        _log(timestamp, type, data, false /* internal */);
        return android::binder::Status::ok();
    }
    android::binder::Status logInternal(const int64_t timestamp,
                                const android::String16 &type,
                                const android::String16 &data) {
        _log(timestamp, type, data, true /*internal */);
        return android::binder::Status::ok();
    }
    android::binder::Status triggerDump() {
#ifdef DEBUG_BUILD
        counter = 0;
        dumper->triggerDump();
#else
        (void*)dumper;
#endif
        return android::binder::Status::ok();
    }

private:
    std::shared_ptr<StorageBackend> storage;
    std::shared_ptr<Dumper> dumper;
    size_t maxMessageSize;
    size_t numEventsBeforeDump;
    std::atomic<size_t> counter;
    void _log(const int64_t timestamp,
              const android::String16 &type,
              const android::String16 &data,
              bool internal) {
        auto typeStr = android::String8(type);
        auto dataStr = android::String8(data);
        if (dataStr.bytes() > maxMessageSize) {
            using namespace rapidjson;
            StringBuffer buffer;
            Writer<StringBuffer> writer(buffer);
            writer.StartObject();
            writer.Key(kOriginalType);
            writer.String(typeStr.string());
            writer.Key(kDataSize);
            writer.Int64(dataStr.bytes());
            writer.EndObject();

            _log(timestamp, android::String16(kOversizedData), android::String16(buffer.GetString()), true);
            return;
        }
        storage->store(
                structured::LogEntry(
                        timestamp,
                        typeStr.string(),
                        dataStr.string(),
                        internal)
        );
        if (++counter >= numEventsBeforeDump) {
            ALOGV("Dumping because counter (%zu) exceeded the configured number of events (%zu)", counter.load(),
                  numEventsBeforeDump);
            dumper->triggerDump();
            counter = 0;
        }
    }
};

void createService(const char* storagePath);

}
