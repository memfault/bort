package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.NumericAgg;
import com.memfault.bort.reporting.StateAgg;
import java.util.Collections;
import java.util.List;

public class Report {
  private final String reportType;

  Report(String reportType) {
    this.reportType = reportType;
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
    return new Counter(name, reportType, sumInReport);
  }

  /**
   * Keeps track of a distribution of the values recorded during the period.
   *
   * @param name the name of the metric.
   * @param aggregations a list of numeric aggregations to perform on the values recorded
   *                     during the heartbeat period, included as metrics in the heartbeat report.
   */
  public Distribution distribution(String name, List<NumericAgg> aggregations) {
    return new Distribution(name, reportType, aggregations);
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
  private StateTracker stateTracker(String name, List<StateAgg> aggregations) {
    return new StateTracker(name, reportType, aggregations);
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
    return new StringStateTracker(name, reportType, aggregations);
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
    return new BoolStateTracker(name, reportType, aggregations);
  }

  /**
   * Keep track of the latest value of a string property.
   *
   * @param name the name of the metric.
   * @param addLatestToReport if true, includes the latest value of the metric in the heartbeat
   *                          report.
   */
  public StringProperty stringProperty(String name, boolean addLatestToReport) {
    return new StringProperty(name, reportType, addLatestToReport);
  }

  /**
   * Keep track of the latest value of a number property.
   *
   * @param name the name of the metric.
   * @param addLatestToReport if true, includes the latest value of the metric in the heartbeat
   *                          report.
   */
  public NumberProperty numberProperty(String name, boolean addLatestToReport) {
    return new NumberProperty(name, reportType, addLatestToReport);
  }

  /**
   * Track individual events. Replacement for Custom Events.
   *
   * @param name the name of the metric.
   * @param countInReport if true, includes a count of the number of events reported during
   *                      the heartbeat period, in the the heartbeat report.
   */
  public Event event(String name, Boolean countInReport) {
    return new Event(name, reportType, countInReport);
  }
}
