#pragma once

#include <map>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include <log/log_read.h>
#include <log/logprint.h>
#include <log/log_time.h>
#include <log/log_id.h>
#include <utils/String16.h>
#include <reporting.h>

#define CONTINUOUS_LOGCAT_TAG "memfault_clog"
#define CONTINUOUS_LOGCAT_FILE "/data/system/MemfaultDumpster/clog"
#define CONTINUOUS_LOGCAT_CONFIG "/data/system/MemfaultDumpster/clog_config"

#ifdef BORT_UNDER_TEST
#include <log/log.h>
#define ALOGT(...) ((void)ALOG(LOG_WARN, "clog-test", __VA_ARGS__))
#else
#define ALOGT(...)
#endif

static constexpr size_t kDefaultDumpThresholdBytes = 25 * 1024 * 1024; // 25 MB
static constexpr size_t kDefaultDumpThresholdTimeMs = 15 * 60 * 1000; // 15 minutes
static constexpr size_t kDefaultDumpWrappingTimeoutMs = 15 * 60 * 1000; // 15 minutes

namespace memfault {

class ContinuousLogcatConfig {
  public:
    explicit ContinuousLogcatConfig(
        bool started,
        std::vector<std::string> filter_specs,
        size_t dump_threshold_bytes,
        uint64_t dump_threshold_time_ms,
        uint64_t dump_wrapping_timeout_ms)
      : started_(started),
        dump_threshold_bytes_(dump_threshold_bytes),
        dump_threshold_time_ms_(dump_threshold_time_ms),
        dump_wrapping_timeout_ms_(dump_wrapping_timeout_ms),
        filter_specs_(filter_specs) {}
    ContinuousLogcatConfig()
      : started_(false),
        dump_threshold_bytes_(kDefaultDumpThresholdBytes),
        dump_threshold_time_ms_(kDefaultDumpThresholdTimeMs),
        dump_wrapping_timeout_ms_(kDefaultDumpWrappingTimeoutMs),
        filter_specs_({}) {}

    void restore_config(const std::string &path = CONTINUOUS_LOGCAT_CONFIG);
    void persist_config(const std::string &path = CONTINUOUS_LOGCAT_CONFIG);

    inline bool started() { return started_; }
    inline size_t dump_threshold_bytes() { return dump_threshold_bytes_; }
    inline uint64_t dump_threshold_time_ms() { return dump_threshold_time_ms_; }
    inline uint64_t dump_wrapping_timeout_ms() { return dump_wrapping_timeout_ms_; }
    inline const std::vector<std::string>& filter_specs() { return filter_specs_; }

    void set_started(bool started) { started_ = started; }
    void set_dump_threshold_bytes(size_t dump_threshold_bytes) { dump_threshold_bytes_ = dump_threshold_bytes; }
    void set_dump_threshold_time_ms(uint64_t dump_threshold_time_ms) { dump_threshold_time_ms_ = dump_threshold_time_ms; }
    void set_dump_wrapping_timeout_ms(uint64_t dump_wrapping_timeout_ms) { dump_wrapping_timeout_ms_ = dump_wrapping_timeout_ms; }
    void set_filter_specs(const std::vector<std::string>& filter_specs) { filter_specs_ = filter_specs; }
  private:
    bool started_;
    size_t dump_threshold_bytes_;
    uint64_t dump_threshold_time_ms_;
    uint64_t dump_wrapping_timeout_ms_;
    std::vector<std::string> filter_specs_;
};

class ContinuousLogcat {
  public:
    ContinuousLogcat();
    void reconfigure(
        const std::vector<std::string>& filter_specs,
        size_t dump_threshold_bytes,
        uint64_t dump_threshold_time_ms,
        uint64_t dump_wrapping_timeout_ms
    );

    void start();
    void stop();
    void join();
    void request_dump();

   private:
    void interrupt_reader_thread();
    void run();
    void dump_output(bool ignore_thresholds = false);
    void dump_output_to_dropbox();

    std::mutex log_lock;

    std::unique_ptr<struct logger_list, decltype(&android_logger_list_close)> logger_list;
    std::unique_ptr<AndroidLogFormat, decltype(&android_log_format_free)> log_format;
    std::vector<log_id_t> buffers{};
    std::map<log_id_t, const char*> log_names;

    std::thread reader_thread;
    size_t total_bytes_written;
    uint64_t last_collection_uptime_ms;
    int output_fd;
    FILE *output_fp;
    ContinuousLogcatConfig config;

    std::unique_ptr<Report> report_;
    std::unique_ptr<Counter> log_buffer_expired_counter_;
};

};

#if PLATFORM_SDK_VERSION >= 30
extern "C" {
void LogdClose(struct logger_list* logger_list);
}
#endif
