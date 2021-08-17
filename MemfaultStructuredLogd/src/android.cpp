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
#include "config.h"
#include "log.h"
#include "logger.h"
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

    backend->store(LogEntry(elapsedRealtimeNano(), "rtc.sync", buffer.GetString(), true /* internal */));
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

void createService(const char* storagePath) {
    StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(storagePath, readBootId());
    Config::SharedPtr config = std::make_shared<StoredConfig>(storage);
    storage->addStorageEmtpyListener([&storage]() { logRtcSync(storage, getTimeInMsSinceEpoch()); });
    logRtcSync(storage, getTimeInMsSinceEpoch());

    std::shared_ptr<Dumper> dumper = std::make_shared<Dumper>(STRUCTURED_DUMP_FILE, config, storage, sendToDropBox,
                                                              [](){ return isDropBoxReady(); },
                                                              config->getDumpPeriodMs(),
                                                              true);
    RateLimiterConfig rateLimiterConfig = config->getRateLimiterConfig();
    std::unique_ptr<TokenBucketRateLimiter> rateLimiter = std::make_unique<TokenBucketRateLimiter>(rateLimiterConfig, []{
        return uint64_t(elapsedRealtime());
    });
    std::unique_ptr<Logger> logger = std::make_unique<Logger>(
            storage, dumper, config, rateLimiter
    );
    sp<LoggerService> loggerService(new LoggerService(logger));
    defaultServiceManager()->addService(
            String16(STRUCTURED_SERVICE_NAME),
            loggerService,
            true /* allowIsolated */);

    signal(SIGTERM, signal_handler);
    std::thread dumpThread(&Dumper::run, dumper);

    // Removed until we figure out how to either rate-limit, or use a metric instead.
//    std::thread timeChangeDetector(detectTimeChanges, [storage](uint64_t time){
//        logRtcSync(storage, time);
//        ALOGV("time updated: %" PRIu64, time);
//    });

    // This log is used in E2E testing to signal test readiness:
    ALOGT("MemfaultStructuredLogd ready");

    IPCThreadState::self()->joinThreadPool();

    // rtc change detetor handles sigterm
//    timeChangeDetector.join();

    dumper->terminate();
    dumpThread.join();

    ALOGI("Terminating gracefully");
}

}
#endif
