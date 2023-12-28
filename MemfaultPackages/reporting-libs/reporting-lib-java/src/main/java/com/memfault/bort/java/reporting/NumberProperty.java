package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import java.util.Collections;

import static com.memfault.bort.reporting.DataType.DOUBLE;
import static com.memfault.bort.reporting.MetricType.PROPERTY;
import static com.memfault.bort.reporting.NumericAgg.LATEST_VALUE;
import static java.util.Collections.singletonList;

public class NumberProperty extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = DOUBLE;
  private static final boolean CARRY_OVER_VALUE = true;

  NumberProperty(String eventName, String reportType, boolean addLatestToReport) {
    super(eventName, reportType,
        addLatestToReport ? singletonList(LATEST_VALUE) : Collections.emptyList(), METRIC_TYPE,
        DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void update(Float value) {
    update(value.doubleValue(), timestamp());
  }

  public void update(Float value, Long timestampMs) {
    update(value.doubleValue(), timestampMs);
  }

  public void update(Long value) {
    update(value.doubleValue(), timestamp());
  }

  public void update(Long value, Long timestampMs) {
    update(value.doubleValue(), timestampMs);
  }

  public void update(Integer value) {
    update(value.doubleValue(), timestamp());
  }

  public void update(Integer value, Long timestampMs) {
    update(value.doubleValue(), timestampMs);
  }

  public void update(Double value) {
    update(value, timestamp());
  }

  public void update(Double value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
