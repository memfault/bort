package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.StateAgg;
import java.util.ArrayList;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.PROPERTY;

public class StringStateTracker extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = STRING;
  private static final Boolean CARRY_OVER_VALUE = true;

  StringStateTracker(String eventName, String reportType, ArrayList<StateAgg> aggregations,
      boolean internal) {
    super(eventName, reportType, aggregations, internal, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void state(String state) {
    state(state, this.timestamp());
  }

  public void state(String state, Long timestampMs) {
    addMetric(state, timestampMs);
  }
}
