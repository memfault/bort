package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import java.util.Collections;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.EVENT;
import static com.memfault.bort.reporting.NumericAgg.COUNT;

public class Event extends Metric {

  private static final MetricType METRIC_TYPE = EVENT;
  private static final DataType DATA_TYPE = STRING;
  private static final Boolean CARRY_OVER_VALUE = false;

  Event(String eventName, String reportType, Boolean countInReport) {
    super(eventName, reportType, countInReport ? Collections.singletonList(COUNT) :
        Collections.EMPTY_LIST, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void add(String value) {
    add(value, timestamp());
  }

  public void add(String value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
