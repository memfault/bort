#ifdef __ANDROID__
#define LOG_NDEBUG 0
#define LOG_TAG "structured"

#include <chrono>
#include <csignal>
#include <string>
#include <thread>

#include <unistd.h>

#include <android/os/DropBoxManager.h>
#include <log/log.h>
#include <utils/SystemClock.h>
#include <cutils/properties.h>

#include "android.h"
#include "logwriter.h"
#include "timeutil.h"
#include "storage.h"

using namespace android;

namespace structured {

static void signal_handler(int signal) {
    if (signal == SIGTERM || signal == SIGINT) {
        ALOGI("Attempting to gracefuly exit due to SIGTERM/SIGINT");
        IPCThreadState::self()->stopProcess();
    }
}

static void logRtcSync(std::shared_ptr<StorageBackend> backend, uint64_t time) {
    rapidjson::StringBuffer buffer;
    rapidjson::Writer<rapidjson::StringBuffer> writer(buffer);

    writer.StartObject();
    writer.Key("epoch_ms");
    writer.Int64(time);
    writer.EndObject();

    backend->store(LogEntry(elapsedRealtimeNano(), "rtc.sync", buffer.GetString(), true /* internal */), 0 /* cost */);
}

static bool isDropBoxReady() {
    return defaultServiceManager()->checkService(android::String16("dropbox")) != NULL;
}

bool sendToDropBox(int nEvents, std::string file) {
    using namespace android::binder;
    using namespace android::os;

    // We always add an rtc sync event through an empty storage listener but let's avoid handling
    // log files that only have that RTC event.
    if (nEvents <= 1) return false;

    std::unique_ptr<DropBoxManager> dropbox(new DropBoxManager());
    Status status = dropbox->addFile(String16(STRUCTURED_DROPBOX_TAG), file, 0);
    if (!status.isOk()) {
        ALOGE("Could not add %s to dropbox", file.c_str());
        return false;
    }
    return true;
}

static std::string readBootId() {
    std::ifstream ifs("/proc/sys/kernel/random/boot_id");
    std::string id;
    ifs >> id;
    return id;
}

static uintmax_t property_get_umax(
        const char *key,
        uintmax_t lower,
        uintmax_t upper,
        uintmax_t defaultValue) {
    if (!key) return defaultValue;

    char buf[PROPERTY_VALUE_MAX] {'\0'};
    char *end = nullptr;

    int len = property_get(key, buf, "");
    if (len == 0) return defaultValue;

    uintmax_t result = strtoumax(buf, &end, 10);
    if (result == UINTMAX_MAX && errno == ERANGE) {
        return defaultValue;
    } else if (result < lower || result > upper) {
        return defaultValue;
    } else if (end == buf) {
        return defaultValue;
    }
    return result;
}

static uint32_t property_get_uint32(
        const char *key,
        uint32_t defaultValue,
        uint32_t lower=0,
        uint32_t upper=UINT32_MAX) {
    return (uint32_t)property_get_umax(key, lower, upper, defaultValue);
}

static uint64_t property_get_uint64(
        const char *key,
        uint64_t defaultValue,
        uint64_t lower=0,
        uint64_t upper=UINT64_MAX) {
    return (uint64_t)property_get_umax(key, lower, upper, defaultValue);
}

void createService(const char* storagePath) {
    uint32_t rateLimitCapacity = property_get_uint32(RATE_LIMIT_CAPACITY_PROPERTY, RATE_LIMIT_CAPACITY);
    uint32_t rateLimitInitialCapacity = property_get_uint32(RATE_LIMIT_INITIAL_CAPACITY_PROPERTY,
                                                          RATE_LIMIT_INITIAL_CAPACITY);
    uint64_t msPerToken = property_get_uint64(RATE_LIMIT_PERIOD_MS_PROPERTY, RATE_LIMIT_PERIOD_MS);

    TokenBucketRateLimiter limiter(msPerToken, rateLimitCapacity, rateLimitInitialCapacity, []{
        return uint64_t(elapsedRealtime());
    });
    StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(storagePath, readBootId(), limiter);
    storage->addStorageEmtpyListener([&storage]() { logRtcSync(storage, getTimeInMsSinceEpoch()); });
    logRtcSync(storage, getTimeInMsSinceEpoch());

    std::shared_ptr<Dumper> dumper = std::make_shared<Dumper>(STRUCTURED_DUMP_FILE, storage, sendToDropBox,
                                                              [](){ return isDropBoxReady(); },
                                                              true);

    size_t maxMessageSize = size_t(property_get_uint32(MAX_MESSAGE_SIZE_BYTES_PROPERTY, MAX_MESSAGE_SIZE_BYTES));
    size_t numEventsBeforeDump = size_t(property_get_uint32(NUM_EVENTS_BEFORE_DUMP_PROPERTY, NUM_EVENTS_BEFORE_DUMP));
    sp<LoggerImpl> logger(new LoggerImpl(storage, dumper, maxMessageSize, numEventsBeforeDump));
    defaultServiceManager()->addService(
            String16(STRUCTURED_SERVICE_NAME),
            logger,
            true /* allowIsolated */);

    signal(SIGTERM, signal_handler);
    std::thread dumpThread(&Dumper::run, dumper);

    std::thread timeChangeDetector(detectTimeChanges, [storage](uint64_t time){
        logRtcSync(storage, time);
        ALOGV("time updated: %" PRIu64, time);
    });

    IPCThreadState::self()->joinThreadPool();

    // rtc change detetor handles sigterm
    timeChangeDetector.join();

    dumper->terminate();
    dumpThread.join();

    ALOGI("Terminating gracefully");
}

}
#endif
