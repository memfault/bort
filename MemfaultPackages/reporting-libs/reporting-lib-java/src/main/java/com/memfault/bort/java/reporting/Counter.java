package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.NumericAgg;
import java.util.Collections;

import static com.memfault.bort.reporting.DataType.DOUBLE;
import static com.memfault.bort.reporting.MetricType.COUNTER;

public class Counter extends Metric {

  private static final MetricType METRIC_TYPE = COUNTER;
  private static final DataType DATA_TYPE = DOUBLE;
  private static final boolean CARRY_OVER_VALUE = false;

  Counter(String name, String reportType, boolean sumInReport) {
    super(name, reportType,
        sumInReport ? Collections.singletonList(NumericAgg.SUM) : Collections.emptyList(),
        METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void increment() {
    incrementBy(1);
  }

  public void incrementBy(Integer byDouble) {
    incrementBy(byDouble.doubleValue(), timestamp());
  }

  public void incrementBy(Integer byDouble, Long timestampMs) {
    incrementBy(byDouble.doubleValue(), timestampMs);
  }

  public void incrementBy(Double byDouble) {
    incrementBy(byDouble, timestamp());
  }

  public void incrementBy(Double byDouble, Long timestampMs) {
    addMetric(byDouble, timestampMs);
  }
}
