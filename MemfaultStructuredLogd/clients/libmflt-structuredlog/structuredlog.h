#pragma once

#ifdef __cplusplus
#include <mutex>
#include <string>
#include <memory>

namespace memfault {

struct Internals;

class StructuredLogger {
public:
  explicit StructuredLogger();
  ~StructuredLogger();

  uint64_t timestamp();
  void log(long timestamp, const std::string &tag, const std::string &message);
  void log(const std::string &tag, const std::string &message);
  void finishReport(const std::string &json);
  void addValue(const std::string &json);

private:
  void ensureServiceLocked();

  std::unique_ptr<Internals> mInt;
};
}
#endif

#ifdef __cplusplus
extern "C" {
#endif
typedef void* structured_logger_t;

structured_logger_t structured_log_new();
void structured_log_destroy(structured_logger_t self);
void structured_log_with_ts(structured_logger_t self, uint64_t timestamp, const char* tag, const char *message);
void structured_log(structured_logger_t self, const char* tag, const char *message);
uint64_t structured_log_timestamp(structured_logger_t self);

void structured_log_finish_report(structured_logger_t self, const char* json);
void structured_log_add_value(structured_logger_t self, const char* json);

#ifdef __cplusplus
}
#endif
