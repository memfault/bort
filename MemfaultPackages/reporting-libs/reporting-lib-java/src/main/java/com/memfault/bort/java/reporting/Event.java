package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.NumericAgg;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.EVENT;
import static com.memfault.bort.reporting.NumericAgg.COUNT;

public class Event extends Metric {

  private static final MetricType METRIC_TYPE = EVENT;
  private static final DataType DATA_TYPE = STRING;
  private static final Boolean CARRY_OVER_VALUE = false;

  Event(String eventName, String reportType, Boolean countInReport, boolean internal) {
    super(eventName, reportType, internal, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
    if (countInReport) {
      aggregations.add(COUNT);
    }
  }

  public void add(String value) {
    add(value, this.timestamp());
  }

  public void add(String value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
