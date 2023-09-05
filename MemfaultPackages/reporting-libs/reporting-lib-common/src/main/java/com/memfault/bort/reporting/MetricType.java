package com.memfault.bort.reporting;

public enum MetricType {
  COUNTER("counter"), GAUGE("gauge"), PROPERTY("property"), EVENT("event");

  public final String value;

  MetricType(String s) {
    this.value = s;
  }
}
