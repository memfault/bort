package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.NumericAgg;
import com.memfault.bort.reporting.RemoteMetricsService;
import com.memfault.bort.reporting.StateAgg;
import java.util.Collections;
import java.util.List;

public class Report {
  private final RemoteMetricsService remoteMetricsService;
  private final String reportType;
  /** Nullable. */
  private final String reportName;

  Report(RemoteMetricsService remoteMetricsService, String reportType, String reportName) {
    this.remoteMetricsService = remoteMetricsService;
    this.reportType = reportType;
    this.reportName = reportName;
  }

  /**
   * Aggregates the total count at the end of the period.
   *
   * @param name the name of the metric.
   */
  public Counter counter(String name) {
    return counter(name, true);
  }

  /**
   * Aggregates the total count at the end of the period.
   *
   * @param name the name of the metric.
   * @param sumInReport if true, includes the sum of all counts in the heartbeat report.
   */
  public Counter counter(String name, boolean sumInReport) {
    return new Counter(remoteMetricsService, name, reportType, sumInReport, reportName);
  }

  /**
   * All-purpose success and failure counter for any sync-like events.
   * <p>
   * See <a href="https://mflt.io/connectivity-sync">Memfault Core Metrics -
   * Periodic Connectivity</a>.
   * </p>
   */
  public SuccessOrFailure sync() {
    return sync(true);
  }

  /**
   * All-purpose success and failure counter for any sync-like events.
   * <p>
   * See <a href="https://mflt.io/connectivity-sync">Memfault Core Metrics -
   * Periodic Connectivity</a>.
   * </p>
   *
   * @param sumInReport if true, includes the sum of all counts in the heartbeat report.
   */
  public SuccessOrFailure sync(boolean sumInReport) {
    return successOrFailure("sync", sumInReport);
  }

  /**
   * Counts the number of success and failures of a custom metric type in the period.
   * <p>
   * Prefer using underscores as separators in the metric name and avoiding spaces.
   * </p>
   *
   * @param name the name of the metric.
   */
  public SuccessOrFailure successOrFailure(String name) {
    return successOrFailure(name, true);
  }

  /**
   * Counts the number of success and failures of a custom metric type in the period.
   * <p>
   * Prefer using underscores as separators in the metric name and avoiding spaces.
   * </p>
   *
   * @param name the name of the metric.
   * @param sumInReport if true, includes the sum of successes and failures in the heartbeat report.
   */
  public SuccessOrFailure successOrFailure(String name, boolean sumInReport) {
    if (name.trim().isEmpty()) {
      throw new IllegalArgumentException(String.format("Name '%s' must not be blank.", name));
    }

    Counter successCounter = counter(String.format("%s_successful", name), sumInReport);
    Counter failureCounter = counter(String.format("%s_failure", name), sumInReport);

    return new SuccessOrFailure(successCounter, failureCounter);
  }

  /**
   * Keeps track of a distribution of the values recorded during the period.
   *
   * @param name the name of the metric.
   * @param aggregations a list of numeric aggregations to perform on the values recorded
   *                     during the heartbeat period, included as metrics in the heartbeat report.
   */
  public Distribution distribution(String name, List<NumericAgg> aggregations) {
    return new Distribution(remoteMetricsService, name, reportType, aggregations, reportName);
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with enums.
   * </p>
   *
   * @param name the name of the metric.
   * @param aggregations a list of state aggregations to perform on the values recorded
   *                     during the heartbeat period, included as metrics in the heartbeat report.
   */
  public StateTracker stateTracker(String name, List<StateAgg> aggregations) {
    return new StateTracker(remoteMetricsService, name, reportType, aggregations, reportName);
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with string representations of state.
   * </p>
   *
   * @param name the name of the metric.
   * @param aggregations a list of state aggregations to perform on the values recorded
   *                     during the heartbeat period, included as metrics in the heartbeat report.
   */
  public StringStateTracker stringStateTracker(String name, List<StateAgg> aggregations) {
    return new StringStateTracker(remoteMetricsService, name, reportType, aggregations, reportName);
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with boolean representations of state.
   * </p>
   *
   * @param name the name of the metric.
   */
  public BoolStateTracker boolStateTracker(String name) {
    return boolStateTracker(name, Collections.emptyList());
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with string representations of state.
   * </p>
   *
   * @param name the name of the metric.
   * @param aggregations a list of state aggregations to perform on the values recorded
   *                     during the heartbeat period, included as metrics in the heartbeat report.
   */
  public BoolStateTracker boolStateTracker(String name, List<StateAgg> aggregations) {
    return new BoolStateTracker(remoteMetricsService, name, reportType, aggregations, reportName);
  }

  /**
   * Keep track of the latest value of a string property.
   *
   * @param name the name of the metric.
   * @param addLatestToReport if true, includes the latest value of the metric in the heartbeat
   *                          report.
   */
  public StringProperty stringProperty(String name, boolean addLatestToReport) {
    return new StringProperty(remoteMetricsService, name, reportType, addLatestToReport,
        reportName);
  }

  /**
   * Keep track of the latest value of a number property.
   *
   * @param name the name of the metric.
   * @param addLatestToReport if true, includes the latest value of the metric in the heartbeat
   *                          report.
   */
  public NumberProperty numberProperty(String name, boolean addLatestToReport) {
    return new NumberProperty(remoteMetricsService, name, reportType, addLatestToReport,
        reportName);
  }

  /**
   * Track individual events. Replacement for Custom Events.
   *
   * @param name the name of the metric.
   * @param countInReport if true, includes a count of the number of events reported during
   *                      the heartbeat period, in the heartbeat report.
   * @param latestInReport if true, includes the latest event reported during the heartbeat period,
   *                       in the heartbeat report.
   */
  public Event event(String name, boolean countInReport, boolean latestInReport) {
    return new Event(remoteMetricsService, name, reportType, countInReport, latestInReport,
        reportName);
  }
}
