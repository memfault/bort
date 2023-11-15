package com.memfault.bort.reporting;

public enum NumericAgg implements AggregationType {
  /**
   * Minimum value seen during the period.
   */
  MIN,

  /**
   * Maximum value seen during the period.
   */
  MAX,

  /**
   * Sum of all values seen during the period.
   */
  SUM,

  /**
   * Mean value seen during the period.
   */
  MEAN,

  /**
   * Number of values seen during the period.
   */
  COUNT,

  LATEST_VALUE,
  // Future: more aggregations e.g. Std Dev, Percentile
}
