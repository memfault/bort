package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.NumericAgg;
import com.memfault.bort.reporting.StateAgg;
import java.util.ArrayList;

public class Report {
  private final String REPORT_TYPE;

  Report(String reportType) {
    REPORT_TYPE = reportType;
  }

  /**
   * Aggregates the total count at the end of the period.
   */
  public Counter counter(String name) {
    return counter(name, true, false);
  }

  /**
   * Aggregates the total count at the end of the period.
   */
  public Counter counter(String name, boolean sumInReport) {
    return counter(name, sumInReport, false);
  }

  private Counter counter(String name, boolean sumInReport, boolean internal) {
    return new Counter(name, REPORT_TYPE, sumInReport, internal);
  }

  /**
   * Keeps track of a distribution of the values recorded during the period.
   *
   * One metric will be generated for each aggregation.
   */
  public Distribution distribution(String name, ArrayList<NumericAgg> aggregations) {
    return distribution(name, aggregations, false);
  }

  private Distribution distribution(String name, ArrayList<NumericAgg> aggregations,
      boolean internal) {
    return new Distribution(name, REPORT_TYPE, aggregations, internal);
  }

  /**
   * Tracks total time spent in each state during the report period.
   *
   * For use with enums.
   */
  public StateTracker stateTracker(String name, ArrayList<StateAgg> aggregations) {
    return stateTracker(name, aggregations, false);
  }

  private StateTracker stateTracker(String name, ArrayList<StateAgg> aggregations,
      boolean internal) {
    return new StateTracker(name, REPORT_TYPE, aggregations, internal);
  }

  /**
   * Tracks total time spent in each state during the report period.
   *
   * For use with string representations of state.
   */
  public StringStateTracker stringStateTracker(String name, ArrayList<StateAgg> aggregations) {
    return stringStateTracker(name, aggregations, false);
  }

  private StringStateTracker stringStateTracker(String name, ArrayList<StateAgg> aggregations,
      boolean internal) {
    return new StringStateTracker(name, REPORT_TYPE, aggregations, internal);
  }

  /**
   * Tracks total time spent in each state during the report period.
   *
   * For use with string representations of state.
   */
  public BoolStateTracker boolStateTracker(String name) {
    return boolStateTracker(name, new ArrayList<>(), false);
  }

  /**
   * Tracks total time spent in each state during the report period.
   *
   * For use with string representations of state.
   */
  public BoolStateTracker boolStateTracker(String name, ArrayList<StateAgg> aggregations) {
    return boolStateTracker(name, aggregations, false);
  }

  private BoolStateTracker boolStateTracker(String name, ArrayList<StateAgg> aggregations,
      boolean internal) {
    return new BoolStateTracker(name, REPORT_TYPE, aggregations, internal);
  }

  /**
   * Keep track of the latest value of a string property.
   */
  public StringProperty stringProperty(String name, Boolean addLatestToReport) {
    return stringProperty(name, addLatestToReport, false);
  }

  private StringProperty stringProperty(String name, Boolean addLatestToReport, boolean internal) {
    return new StringProperty(name, REPORT_TYPE, addLatestToReport, internal);
  }

  public NumberProperty numberProperty(String name, Boolean addLatestToReport) {
    return numberProperty(name, addLatestToReport, false);
  }

  private NumberProperty numberProperty(String name, Boolean addLatestToReport, boolean internal) {
    return new NumberProperty(name, REPORT_TYPE, addLatestToReport, internal);
  }

  /**
   * Track individual events. Replacement for Custom Events.
   */
  public Event event(String name, Boolean countInReport) {
    return event(name, countInReport, false);
  }

  private Event event(String name, Boolean countInReport, Boolean internal) {
    return new Event(name, REPORT_TYPE, countInReport, internal);
  }
}
