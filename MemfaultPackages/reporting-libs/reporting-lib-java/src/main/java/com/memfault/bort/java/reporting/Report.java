package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.NumericAgg;
import com.memfault.bort.reporting.StateAgg;
import java.util.ArrayList;

public class Report {
  private final String reportType;

  Report(String reportType) {
    this.reportType = reportType;
  }

  /**
   * Aggregates the total count at the end of the period.
   */
  public Counter counter(String name) {
    return counter(name, true);
  }

  /**
   * Aggregates the total count at the end of the period.
   */
  public Counter counter(String name, boolean sumInReport) {
    return new Counter(name, reportType, sumInReport);
  }

  /**
   * Keeps track of a distribution of the values recorded during the period.
   * <p>
   * One metric will be generated for each aggregation.
   * </p>
   */
  public Distribution distribution(String name, ArrayList<NumericAgg> aggregations) {
    return new Distribution(name, reportType, aggregations);
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with enums.
   * </p>
   */
  private StateTracker stateTracker(String name, ArrayList<StateAgg> aggregations) {
    return new StateTracker(name, reportType, aggregations);
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with string representations of state.
   * </p>
   */
  public StringStateTracker stringStateTracker(String name, ArrayList<StateAgg> aggregations) {
    return new StringStateTracker(name, reportType, aggregations);
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with string representations of state.
   * </p>
   */
  public BoolStateTracker boolStateTracker(String name) {
    return boolStateTracker(name, new ArrayList<>());
  }

  /**
   * Tracks total time spent in each state during the report period.
   * <p>
   * For use with string representations of state.
   * </p>
   */
  public BoolStateTracker boolStateTracker(String name, ArrayList<StateAgg> aggregations) {
    return new BoolStateTracker(name, reportType, aggregations);
  }

  /**
   * Keep track of the latest value of a string property.
   */
  public StringProperty stringProperty(String name, Boolean addLatestToReport) {
    return new StringProperty(name, reportType, addLatestToReport);
  }

  public NumberProperty numberProperty(String name, Boolean addLatestToReport) {
    return new NumberProperty(name, reportType, addLatestToReport);
  }

  /**
   * Track individual events. Replacement for Custom Events.
   */
  public Event event(String name, Boolean countInReport) {
    return new Event(name, reportType, countInReport);
  }
}
