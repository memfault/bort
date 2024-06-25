#pragma once

#ifdef __cplusplus
#include <string>
#include <memory>

namespace memfault {

struct Internals;

class StructuredLogger {
public:
  explicit StructuredLogger();
  ~StructuredLogger();

  void addValue(const std::string &json);
  void startReport(const std::string &json);
  void finishReport(const std::string &json);

private:
  void ensureServiceLocked();

  std::unique_ptr<Internals> mInt;
};
}
#endif
