package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.NumericAgg;
import java.util.ArrayList;

import static com.memfault.bort.reporting.DataType.DOUBLE;
import static com.memfault.bort.reporting.MetricType.COUNTER;

public class Counter extends Metric {

  private static final MetricType METRIC_TYPE = COUNTER;
  private static final DataType DATA_TYPE = DOUBLE;
  private static final Boolean CARRY_OVER_VALUE = false;

   Counter(String name, String reportType, Boolean sumInReport,  Boolean internal) {
    super(name, reportType, new ArrayList<>(), internal, METRIC_TYPE, DATA_TYPE,
        CARRY_OVER_VALUE);
    if (sumInReport) {
      aggregations.add(NumericAgg.SUM);
    }
  }

  public void increment() {
    incrementBy(1);
  }

  public void incrementBy(Integer byDouble) {
    incrementBy(byDouble.doubleValue(), this.timestamp());
  }

  public void incrementBy(Integer byDouble, Long timestampMs) {
    incrementBy(byDouble.doubleValue(), timestampMs);
  }

  public void incrementBy(Double byDouble) {
    incrementBy(byDouble, this.timestamp());
  }

  public void incrementBy(Double byDouble, Long timestampMs) {
    addMetric(byDouble, timestampMs);
  }
}
