package com.memfault.bort.reporting;

public enum StateAgg implements AggregationType {
  /**
   * Metric per state, reporting time spent in that state during the period.
   */
  TIME_TOTALS,

  /**
   * Metric per state, reporting time spent in that state during the period (per hour).
   */
  TIME_PER_HOUR,

  /**
   * The latest value reported for this property.
   */
  LATEST_VALUE,
}
