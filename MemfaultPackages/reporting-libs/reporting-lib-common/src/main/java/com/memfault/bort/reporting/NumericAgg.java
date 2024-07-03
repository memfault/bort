package com.memfault.bort.reporting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum NumericAgg implements AggregationType {
  /**
   * Minimum value seen during the period.
   */
  MIN("MIN"),

  /**
   * Maximum value seen during the period.
   */
  MAX("MAX"),

  /**
   * Sum of all values seen during the period.
   */
  SUM("SUM"),

  /**
   * Mean value seen during the period.
   */
  MEAN("MEAN"),

  /**
   * Number of values seen during the period.
   */
  COUNT("COUNT"),

  LATEST_VALUE("LATEST_VALUE"),
  // Future: more aggregations e.g. Std Dev, Percentile

  /**
   * The total amount the value "dropped" during the period.
   */
  VALUE_DROP("VALUE_DROP"),
  ;

  public final String value;

  NumericAgg(String s) {
    this.value = s;
  }

  public static final Map<String, NumericAgg> lookup = new HashMap<>();

  static {
    for (NumericAgg s : EnumSet.allOf(NumericAgg.class)) {
      lookup.put(s.value, s);
    }
  }

  @Override
  public String value() {
    return value;
  }
}
