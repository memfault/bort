package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.StateAgg;
import java.util.List;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.PROPERTY;

public class StringStateTracker extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = STRING;
  private static final boolean CARRY_OVER_VALUE = true;

  StringStateTracker(String eventName, String reportType, List<StateAgg> aggregations,
      String reportName) {
    super(eventName, reportType, aggregations, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE,
        reportName);
  }

  public void state(String state) {
    state(state, timestamp());
  }

  public void state(String state, Long timestampMs) {
    addMetric(state, timestampMs);
  }
}
