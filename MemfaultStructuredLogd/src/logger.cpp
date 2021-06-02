#include <rapidjson/stringbuffer.h>
#include <rapidjson/writer.h>
#include "logger.h"
#include "log.h"

static constexpr char kOriginalType[] = "original_type";
static constexpr char kOversizedData[] = "oversized_data";
static constexpr char kDataSize[] = "size";

namespace structured {

void Logger::log(int64_t timestamp, const std::string &type, const std::string &data, bool internal) {
    if (storage->getAvailableSpace() < config->getMinStorageThreshold()) {
        ALOGE("Log with type %s dropped because available space is lower than the configured threshold", type.c_str());
        return;
    }

    if (!rateLimiter->take(1)) {
        ALOGV("Entry ignored by rate limiter");
        return;
    }

    if (data.size() > config->getMaxMessageSize()) {
        using namespace rapidjson;
        StringBuffer buffer;
        Writer<StringBuffer> writer(buffer);
        writer.StartObject();
        writer.Key(kOriginalType);
        writer.String(type.c_str());
        writer.Key(kDataSize);
        writer.Uint64(data.size());
        writer.EndObject();

        log(timestamp, kOversizedData, buffer.GetString(), true);
        return;
    }

    storage->store(LogEntry(timestamp, type, data, internal));

    if (++counter >= config->getNumEventsBeforeDump()) {
        ALOGV("Dumping because counter (%" PRIu32 ") exceeded the configured number of events (%" PRIu32 ")", counter.load(),
              config->getNumEventsBeforeDump());
        dumper->triggerDump();
        counter = 0;
    }
}

void Logger::triggerDump() {
#if defined(BORT_UNDER_TEST) || defined(DEBUG_BUILD)
    counter = 0;
    dumper->triggerDump();
#endif
}

void Logger::reloadConfig(const std::string &configJson) {
    config->updateConfig(configJson);
    rateLimiter->reconfigure(config->getRateLimiterConfig());
    dumper->changeDumpPeriod(config->getDumpPeriodMs());
}

}
