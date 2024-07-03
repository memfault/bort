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

  auto report = std::make_unique<Report>();

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

  auto stringStateTracker = report->stringStateTracker("native_states_cpp", {TIME_TOTALS});
  stringStateTracker->state("on");
  stringStateTracker->state("off");
  stringStateTracker->state("initializing");

  auto boolStateTracker =
    report->boolStateTracker("native_states_bool_cpp", {TIME_PER_HOUR, LATEST_VALUE});
  boolStateTracker->state(true);
  boolStateTracker->state(false);
  boolStateTracker->state(true);

  auto event = report->event("native_event_cpp", true);
  event->add("Hello");
  event->add("world!");

  auto session = std::make_unique<Report>("native_session_cpp");

  auto timestamp = session->timestamp();

  session->startSession(timestamp - 10000);

  auto sessionStringStateTracker = session->stringStateTracker("session_states_cpp", {TIME_TOTALS, LATEST_VALUE});
  sessionStringStateTracker->state("ready", timestamp - 8000);
  sessionStringStateTracker->state("steady", timestamp - 6000);
  sessionStringStateTracker->state("go", timestamp - 4000);

  auto sessionNumericProp = session->numberProperty("session_number_cpp");
  sessionNumericProp->update(0.0, timestamp - 9000);
  sessionNumericProp->update(1.0, timestamp - 1000);

  session->finishSession(timestamp);

  return 0;
}
