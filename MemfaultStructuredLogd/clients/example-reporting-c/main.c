#include <stdio.h>
#include <string.h>

#include <reporting.h>

int main(int argc, char *argv[]) {
  int injectIntoHeartbeat = 0;

  if (argc == 2 && !strcmp("heartbeat", argv[1])) {
    injectIntoHeartbeat = 1;
  }

  metric_report_t report = metric_report_new(injectIntoHeartbeat ? "Heartbeat" : "testing");

  metric_counter_t counter = metric_report_counter(report, "native_counter");
  metric_counter_increment(counter);
  metric_counter_destroy(counter);

  metric_aggregation_t aggrs[] = {MIN, MAX, SUM};
  metric_distribution_t distribution = metric_report_distribution(report, "native_distribution", aggrs, 3);
  metric_distribution_record_double(distribution, 1.0);
  metric_distribution_record_double(distribution, 10.0);
  metric_distribution_destroy(counter);

  metric_string_prop_t string_prop = metric_report_string_prop(report, "native_string");
  metric_string_prop_update(string_prop, "on");
  metric_string_prop_update(string_prop, "off");
  metric_string_prop_destroy(string_prop);

  metric_number_prop_t number_prop = metric_report_number_prop(report, "native_number");
  metric_number_prop_update(number_prop, 0.0);
  metric_number_prop_update(number_prop, 1.0);
  metric_number_prop_destroy(number_prop);

  metric_aggregation_t aggrs2[] = {TIME_TOTALS};
  metric_state_tracker_t state_tracker = metric_report_state_tracker(report, "native_states", aggrs2, 1);
  metric_state_tracker_state(state_tracker, "on");
  metric_state_tracker_state(state_tracker, "off");
  metric_state_tracker_state(state_tracker, "initializing");
  metric_state_tracker_destroy(state_tracker);

  if (!injectIntoHeartbeat) {
    metric_report_finish(report);
  }

  metric_report_destroy(report);

  return 0;
}
