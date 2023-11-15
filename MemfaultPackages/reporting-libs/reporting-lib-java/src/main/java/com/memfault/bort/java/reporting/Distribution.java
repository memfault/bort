package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.NumericAgg;
import java.util.List;

import static com.memfault.bort.reporting.DataType.DOUBLE;
import static com.memfault.bort.reporting.MetricType.GAUGE;

public class Distribution extends Metric {

  private static final MetricType METRIC_TYPE = GAUGE;
  private static final DataType DATA_TYPE = DOUBLE;
  private static final Boolean CARRY_OVER_VALUE = false;

  Distribution(String eventName, String reportType, List<NumericAgg> aggregations) {
    super(eventName, reportType, aggregations, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void record(Long value) {
    record(value.doubleValue(), timestamp());
  }

  public void record(Long value, Long timestampMs) {
    record(value.doubleValue(), timestampMs);
  }

  public void record(Double value) {
    record(value, timestamp());
  }

  public void record(Double value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
