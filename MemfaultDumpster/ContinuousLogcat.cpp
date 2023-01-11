#define LOG_TAG "mflt-clog"

#include "ContinuousLogcat.h"
#include "ContinuousLogcatConfigProto.pb.h"
#include "ScopedRepeatingAlarm.h"

#include <android/os/DropBoxManager.h>

#include <chrono>
#include <cstdio>
#include <fstream>
#include <unordered_set>

#include <inttypes.h>
#include <fcntl.h>
#include <pthread.h>
#include <unistd.h>
#include <utils/String8.h>
#include <utils/String16.h>
#include <utils/SystemClock.h>
#include <reporting.h>

namespace memfault {

static constexpr char kMetricReportName[] = "Heartbeat";
static constexpr char kLogBufferExpiredCounterName[] = "log_buffer_expired_counter";

ContinuousLogcat::ContinuousLogcat() :
    logger_list(nullptr, android_logger_list_close),
    log_format(nullptr, android_log_format_free),
    total_bytes_written(0),
    last_collection_uptime_ms(android::uptimeMillis()) {

  report_ = std::make_unique<Report>(kMetricReportName);
  log_buffer_expired_counter_ = report_->counter(kLogBufferExpiredCounterName);
  // Compute the list of buffers we want to read from. Buffers
  // may vary between platform versions we use the liblog API
  // to match names to buffer ids.
  for (int i = LOG_ID_MIN; i < LOG_ID_MAX; i++) {
    const char* name = android_log_id_to_name((log_id_t)i);
    log_id_t log_id = android_name_to_log_id(name);

    log_names[log_id] = name;

    // ignore binary logs
    bool binary = log_id == LOG_ID_EVENTS || log_id == LOG_ID_SECURITY;
#if PLATFORM_SDK_VERSION > 27
    binary |= log_id == LOG_ID_STATS;
#endif

    if (!binary) {
      buffers.push_back(log_id);
    }
  }

  config.restore_config();
  if (config.started()) {
    this->start();
  }
}

void ContinuousLogcat::start() {
  std::lock_guard<std::mutex> lock(log_lock);
  ALOGT("clog: start (running=%d)", config.started());
  if (!config.started()) {
    output_fd = creat(CONTINUOUS_LOGCAT_FILE, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP);
    output_fp = fdopen(output_fd, "w");
    config.set_started(true);
    config.persist_config();
    std::thread run_thread(&ContinuousLogcat::run, this);
    reader_thread = std::move(run_thread);
    ALOGT("clog: new log file created, thread running");
  }
}

void ContinuousLogcat::stop() {
  std::lock_guard<std::mutex> lock(log_lock);
  ALOGT("clog: stop (running=%d)", config.started());
  if (config.started()) {
    config.set_started(false);
    config.persist_config();

    // Close logger list, when liblog receives the interrupt below
    // it will retry unless the logger list is invalid.
    logger_list.reset(nullptr);

    // Send a SIGALRM so that the blocked reader in liblog returns
    // with -EINTR, we then handle that result in the reader loop.
    pthread_kill(reader_thread.native_handle(), SIGALRM);
  }
}

void ContinuousLogcat::reconfigure(
    const std::vector<std::string>& filter_specs,
    size_t dump_threshold_bytes,
    uint64_t dump_threshold_time_ms,
    uint64_t dump_wrapping_timeout_ms) {
  std::lock_guard<std::mutex> lock(log_lock);
  ALOGT("clog: reconfiguring");

  // readers receive all the logs and decide how to format them through a log_format object
  // log_format controls:
  // - Output formats (i.e. time spec, whether to print nanoseconds, etc)
  // - Filters (i.e. do we want to print this line)
  //
  // When reconfiguring, we rebuild the log_format object (note that this is guarded by log_lock
  // and will remain consistent even if we are currently collecting).
  log_format.reset(android_log_format_new());

  // We current use the same log formats as the Bort periodic logcat collector.
  auto logFormats = {
    AndroidLogPrintFormat::FORMAT_THREADTIME,
    AndroidLogPrintFormat::FORMAT_MODIFIER_TIME_NSEC,
    AndroidLogPrintFormat::FORMAT_MODIFIER_PRINTABLE,
    AndroidLogPrintFormat::FORMAT_MODIFIER_UID,
    AndroidLogPrintFormat::FORMAT_MODIFIER_ZONE,
    AndroidLogPrintFormat::FORMAT_MODIFIER_YEAR,
  };

  // Set the timezone to UTC (i.e. same behavior as logcat when -v UTC is passed)
  setenv("TZ", "UTC", 1);

  for (auto& format : logFormats) {
    android_log_setPrintFormat(log_format.get(), format);
  }

  // add filters
  for (auto &filter : filter_specs) {
    android_log_addFilterString(log_format.get(), filter.c_str());
  }

  config.set_filter_specs(filter_specs);
  config.set_dump_threshold_bytes(dump_threshold_bytes);
  config.set_dump_threshold_time_ms(dump_threshold_time_ms);
  config.set_dump_wrapping_timeout_ms(dump_wrapping_timeout_ms);
  config.persist_config();
}

void ContinuousLogcat::join() {
  if (reader_thread.joinable()) {
    reader_thread.join();
  }
}

void ContinuousLogcat::run() {
  std::unordered_set<log_id_t> first_line_printed;
  log_id_t last_printed_log_id = LOG_ID_MAX;
  log_time last_log_time;

  bool alarm_fired = false;
  bool dump_after_intr = false;

  ScopedRepeatingAlarm alarm(
      [&]() {
        return std::chrono::milliseconds(config.dump_wrapping_timeout_ms());
      },
      [&]() {
        ALOGT("clog: alarm was triggered");
        alarm_fired = true;

        // Send a SIGALRM so that the blocked reader in liblog returns
        // with -EINTR, we then handle that result in the reader loop.
        pthread_kill(reader_thread.native_handle(), SIGALRM);
      }
  );

  memset(&last_log_time, 0, sizeof(last_log_time));

  ALOGT("clog: thread starting");

  while (config.started() || dump_after_intr) {
    /**
     * Initialize the logger. This is done for each logger dump and will happen multiple times
     * during collection, once initially then once each time wrapping behavior occurs.
     *
     * logd wrapping behavior will dump logs to the reader when a specific buffer timestamp is
     * about to expire. Initially, we don't know the exact timestamp so we perform an initial
     * collection in order to compute it. Follow-up runs will pass the last collected time as
     * the wrapping timestamp.
     *
     * This is roughly equivalent to the following manual steps:
     * $ logcat --wrap
     * 05-10 01:50:05.900  2973  2973 D QtiCarrierConfigHelper: WARNING, no carrier configs on phone Id: 0
     * 05-10 01:50:05.902  3031  3031 V DeviceStatisticsService: chargerType=0 batteryLevel=99 totalBatteryCapacity=4036000
     * 05-10 01:50:05.922  2606  2606 D KeyguardUpdateMonitor: handleBatteryUpdate
     * $ logcat --wrap -T "05-10 01:50:05.922"
     * (will block until 05-10 01:50:05.922 is about to expire).
     */

    // don't block when the buffer ends (equivalent to logcat -d), this does not prevent blocking in wrapping scenarios
    int log_mode = ANDROID_LOG_NONBLOCK;

    // if we are not doing an inmmediate dump, use wrapping behavior
    if (!dump_after_intr) {
      log_mode |= ANDROID_LOG_WRAP;
    }

    if (last_log_time.tv_sec == 0 && last_log_time.tv_nsec == 0) {
      logger_list.reset(android_logger_list_alloc(log_mode, 0 /* tail_lines */, 0 /* pid */));
    } else {
      logger_list.reset(android_logger_list_alloc_time(log_mode, last_log_time, 0 /* pid */));
    }


    // do this for each intended buffer
    for (auto& buffer : buffers) {
      // Add all buffers to the collection list
      if (!android_logger_open(logger_list.get(), buffer)) {
        ALOGE("cannot add log buffer with id %d", buffer);
      }
    }

    bool expiry_reported = false;
    while (config.started() || dump_after_intr) {
      struct log_msg log_msg;
      // Read a log entry, this will run once per log line.
      int ret = android_logger_list_read(logger_list.get(), &log_msg);

      if (ret == -EBADF) {
        ALOGT("clog: interrupted via stop signal, will dump");
        dump_after_intr = true;

        // logd socket likely closed, if that was us 'running' will be false
        break;
      }

      if (ret < 0) {
        ALOGT("clog: error while reading: %d\n", ret);
        if (!config.started() || alarm_fired) {
          ALOGT("clog: interrupted via stop signal, will dump");
          alarm_fired = false;
          dump_after_intr = true;
        }
        break;
      }

      if ((log_mode & ANDROID_LOG_WRAP) && !expiry_reported) {
        expiry_reported = true;
        log_buffer_expired_counter_->increment();
      }

      // Convert the logd buffer to the more human-friendly AndroidLogEntry
      AndroidLogEntry entry;
#if PLATFORM_SDK_VERSION <= 29
      int err = android_log_processLogBuffer(&log_msg.entry_v1, &entry);
#else
      int err = android_log_processLogBuffer(&log_msg.entry, &entry);
#endif
      if (err < 0) {
        ALOGE("error processing line: %d\n", err);
        continue;
      }

      // Add dividers identical to those of logcat
      log_id_t log_id = (log_id_t)log_msg.entry.lid;
      bool hasPrinted = true;
      if (first_line_printed.find(log_id) == first_line_printed.end()) {
        first_line_printed.insert(log_id);
        hasPrinted = false;
      }

      if (last_printed_log_id != log_id) {
        char buf[1024];
        auto name = log_names.find(log_id);

        if (name != log_names.end()) {
          snprintf(buf, sizeof(buf), "--------- %s %s\n",
              hasPrinted ? "switch to" : "beginning of", name->second);
          auto len = strlen(buf);
          if (write(output_fd, buf, len) >= 0) {
            total_bytes_written += len;
            last_printed_log_id = log_id;
          } else {
            ALOGW("Failed to write separator to continuous log output");
          }
        }
      }

      // Print the line to the output file.
#if PLATFORM_SDK_VERSION <= 32
      total_bytes_written += android_log_printLogLine(log_format.get(), output_fd, &entry);
#else
      total_bytes_written += android_log_printLogLine(log_format.get(), output_fp, &entry);
#endif

      // Dump to dropbox if thresholds are reached, but if we are in a immediate collection,
      // do this later after all lines are processed.
      if (!dump_after_intr) {
        dump_output();
      }

      last_log_time.tv_sec = log_msg.entry.sec;
      last_log_time.tv_nsec = log_msg.entry.nsec + 1;
    }

    // After a dump caused by an interruption (stop or alarm) reset the dump
    // flag).
    if (dump_after_intr && !(log_mode & ANDROID_LOG_WRAP)) {
      dump_after_intr = false;
      bool ignore_thresholds = !config.started();
      dump_output(ignore_thresholds);
    }
  }

  ALOGT("clog: removing leftover files");
  fclose(output_fp);
  unlink(CONTINUOUS_LOGCAT_FILE);

  ALOGT("clog: stop");
}

void ContinuousLogcat::dump_output(bool ignore_thresholds) {
  if (total_bytes_written == 0) return;

  uint64_t elapsed_ms_since_last_collection = android::uptimeMillis() - last_collection_uptime_ms;
  if (ignore_thresholds || total_bytes_written > config.dump_threshold_bytes() ||
        elapsed_ms_since_last_collection > config.dump_threshold_time_ms()) {
      ALOGT("clog: reached threshold (wrote %zu / %zu), time_ms (%" PRIu64 " / %" PRIu64 "), dumping",
          total_bytes_written,
          config.dump_threshold_bytes(),
          elapsed_ms_since_last_collection,
          config.dump_threshold_time_ms());
      fsync(output_fd);
      fclose(output_fp);
      dump_output_to_dropbox();
      total_bytes_written = 0;
      last_collection_uptime_ms = android::uptimeMillis();
      output_fd = creat(CONTINUOUS_LOGCAT_FILE, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP);
      output_fp = fdopen(output_fd, "w");
  }
}

void ContinuousLogcat::dump_output_to_dropbox() {
  using namespace android::os;
  std::unique_ptr<DropBoxManager> dropbox(new DropBoxManager());
  Status status = dropbox->addFile(String16(CONTINUOUS_LOGCAT_TAG), CONTINUOUS_LOGCAT_FILE, 0);
  if (!status.isOk()) {
    ALOGE("Could not add %s to dropbox", CONTINUOUS_LOGCAT_FILE);
  }
}

void ContinuousLogcatConfig::restore_config(const std::string &path) {
  std::fstream input_config(path, std::ios::in | std::ios::binary);
  if (input_config) {
    ALOGT("Found a persisted config at %s, restoring", path.c_str());
    ContinuousLogcatConfigProto config;
    if (config.ParseFromIstream(&input_config)) {
      if (config.has_started()) started_ = config.started();
      if (config.has_dump_threshold_bytes()) dump_threshold_bytes_ = (size_t)config.dump_threshold_bytes();
      if (config.has_dump_threshold_time_ms()) dump_threshold_time_ms_ = (uint64_t)config.dump_threshold_time_ms();
      if (config.has_dump_wrapping_timeout_ms()) dump_wrapping_timeout_ms_ = (uint64_t)config.dump_wrapping_timeout_ms();

      filter_specs_.clear();
      for (int i = 0; i < config.filter_specs_size(); i++ ){
        filter_specs_.emplace_back(config.filter_specs(i).c_str());
      }
    } else {
      ALOGT("Unable to read persisted config at %s, keeping defaults", path.c_str());
    }
  } else {
    ALOGT("No persisted config, keeping defaults");
  }
}

void ContinuousLogcatConfig::persist_config(const std::string &path) {
  std::fstream output_config(path, std::ios::out | std::ios::binary);
  if (output_config) {
    ContinuousLogcatConfigProto config;
    config.set_started(started_);
    for (auto &it : filter_specs_) {
      config.add_filter_specs(it);
    }
    config.set_dump_threshold_bytes(dump_threshold_bytes_);
    config.set_dump_threshold_time_ms(dump_threshold_time_ms_);
    config.set_dump_wrapping_timeout_ms(dump_wrapping_timeout_ms_);

    if (config.SerializeToOstream(&output_config)) {
      ALOGT("Config persisted to %s", path.c_str());
    } else {
      ALOGT("Failed to persist config to %s", path.c_str());
    }
  } else {
    ALOGT("Could not open %s for writing", path.c_str());
  }
}

}
