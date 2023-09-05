package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.PROPERTY;
import static com.memfault.bort.reporting.NumericAgg.LATEST_VALUE;

public class StringProperty extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = STRING;
  private static final Boolean CARRY_OVER_VALUE = true;

  StringProperty(String eventName, String reportType, Boolean addLatestToReport,
      boolean internal) {
    super(eventName, reportType, internal, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
    if (addLatestToReport) {
      aggregations.add(LATEST_VALUE);
    }
  }

  public void update(String value) {
    update(value, this.timestamp());
  }

  public void update(String value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
