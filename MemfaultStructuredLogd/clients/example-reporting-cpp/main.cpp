#include <cstdio>
#include <memory>
#include <string>

#include <reporting.h>

using namespace memfault;

int main(int argc, char *argv[]) {
  bool injectIntoHeartbeat = false;

  if (argc == 2 && !strcmp("heartbeat", argv[1])) {
    injectIntoHeartbeat = true;
  }

  auto report = std::make_unique<Report>(injectIntoHeartbeat ? "Heartbeat" : "testing");

  auto counter = report->counter("native_counter_cpp");
  counter->increment();

  auto distribution = report->distribution("native_distribution_cpp", {SUM, MIN, MAX});
  distribution->record(1.0);
  distribution->record(10.0);

  auto stringProp = report->stringProperty("native_string_cpp");
  stringProp->update("on");
  stringProp->update("off");

  auto numericProp = report->numberProperty("native_number_cpp");
  numericProp->update(0.0);
  numericProp->update(1.0);

  auto stateTracker = report->stateTracker("native_states_cpp", {TIME_TOTALS});
  stateTracker->state("on");
  stateTracker->state("off");
  stateTracker->state("initializing");

  if (!injectIntoHeartbeat) {
    report->finish();
  }

  return 0;
}
