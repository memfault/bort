package com.memfault.bort.reporting;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

public enum MetricType {
  COUNTER("counter"), GAUGE("gauge"), PROPERTY("property"), EVENT("event");

  public final String value;

  MetricType(String s) {
    this.value = s;
  }

  public static final Map<String, MetricType> lookup = new HashMap<>();

  static {
    for (MetricType s : EnumSet.allOf(MetricType.class)) {
      lookup.put(s.value, s);
    }
  }
}
