package com.memfault.bort.reporting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum StateAgg implements AggregationType {
  /**
   * Metric per state, reporting time spent in that state during the period.
   */
  TIME_TOTALS("TIME_TOTALS"),

  /**
   * Metric per state, reporting time spent in that state during the period (per hour).
   */
  TIME_PER_HOUR("TIME_PER_HOUR"),

  /**
   * The latest value reported for this property.
   */
  LATEST_VALUE("LATEST_VALUE"),
  ;

  public final String value;

  StateAgg(String s) {
    this.value = s;
  }

  public static final Map<String, StateAgg> lookup = new HashMap<>();

  @Override
  public String value() {
    return value;
  }

  static {
    for (StateAgg s : EnumSet.allOf(StateAgg.class)) {
      lookup.put(s.value, s);
    }
  }
}
