package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.StateAgg;
import java.util.List;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.PROPERTY;

public class StateTracker extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = STRING;
  private static final boolean CARRY_OVER_VALUE = true;

  StateTracker(String eventName, String reportType, List<StateAgg> aggregations) {
    super(eventName, reportType, aggregations, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public <T extends Enum<T>> void state(T state) {
    state(state, timestamp());
  }

  public <T extends Enum<T>> void state(T state, Long timestampMs) {
    addMetric(state.toString(), timestampMs);
  }
}
