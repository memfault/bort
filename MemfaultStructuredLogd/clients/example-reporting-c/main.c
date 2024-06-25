#include <stdio.h>
#include <string.h>

#include <reporting.h>

int main(int argc, char *argv[]) {
  int injectIntoHeartbeat = 0;

  if (argc == 2 && !strcmp("heartbeat", argv[1])) {
    injectIntoHeartbeat = 1;
  }

  metric_report_t report = metric_report_new();

  metric_counter_t counter = metric_report_counter(report, "native_counter", true);
  metric_counter_increment(counter);
  metric_counter_destroy(counter);

  metric_aggregation_t aggrs[] = {MIN, MAX, SUM};
  metric_distribution_t distribution = metric_report_distribution(report, "native_distribution", aggrs, 3);
  metric_distribution_record_double(distribution, 1.0);
  metric_distribution_record_double(distribution, 10.0);
  metric_distribution_destroy(counter);

  metric_string_prop_t string_prop = metric_report_string_prop(report, "native_string", true);
  metric_string_prop_update(string_prop, "on");
  metric_string_prop_update(string_prop, "off");
  metric_string_prop_destroy(string_prop);

  metric_number_prop_t number_prop = metric_report_number_prop(report, "native_number", true);
  metric_number_prop_update(number_prop, 0.0);
  metric_number_prop_update(number_prop, 1.0);
  metric_number_prop_destroy(number_prop);

  metric_aggregation_t aggrs2[] = {TIME_TOTALS};
  metric_string_state_tracker_t state_tracker =
    metric_report_string_state_tracker(report, "native_states", aggrs2, 1);
  metric_string_state_tracker_state(state_tracker, "on");
  metric_string_state_tracker_state(state_tracker, "off");
  metric_string_state_tracker_state(state_tracker, "initializing");
  metric_string_state_tracker_destroy(state_tracker);

  metric_event_t event = metric_report_event(report, "native_event", true);
  metric_event_add(event, "Hello c");
  metric_event_add(event, "c world!");
  metric_event_destroy(event);

  metric_report_destroy(report);

  metric_report_t session = metric_session_start("native_session");

  metric_counter_t sessionCounter = metric_report_counter(session, "native_session_counter", true);
  metric_counter_increment(sessionCounter);
  metric_counter_destroy(sessionCounter);

  metric_session_finish_plus_ts(session, 1);

  return 0;
}
