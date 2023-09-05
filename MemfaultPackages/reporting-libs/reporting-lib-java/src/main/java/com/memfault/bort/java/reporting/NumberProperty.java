package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;

import static com.memfault.bort.reporting.DataType.DOUBLE;
import static com.memfault.bort.reporting.MetricType.PROPERTY;
import static com.memfault.bort.reporting.NumericAgg.LATEST_VALUE;

public class NumberProperty extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = DOUBLE;
  private static final Boolean CARRY_OVER_VALUE = true;

  NumberProperty(String eventName, String reportType, Boolean addLatestToReport,
      boolean internal) {
    super(eventName, reportType, internal, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
    if (addLatestToReport) {
      aggregations.add(LATEST_VALUE);
    }
  }

  public void update(Float value) {
    update(value.doubleValue(), this.timestamp());
  }

  public void update(Float value, Long timestampMs) {
    update(value.doubleValue(), timestampMs);
  }

  public void update(Long value) {
    update(value.doubleValue(), this.timestamp());
  }

  public void update(Long value, Long timestampMs) {
    update(value.doubleValue(), timestampMs);
  }

  public void update(Integer value) {
    update(value.doubleValue(), this.timestamp());
  }

  public void update(Integer value, Long timestampMs) {
    update(value.doubleValue(), timestampMs);
  }

  public void update(Double value) {
    update(value, this.timestamp());
  }

  public void update(Double value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
