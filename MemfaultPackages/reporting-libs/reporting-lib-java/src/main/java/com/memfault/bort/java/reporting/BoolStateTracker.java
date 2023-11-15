package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.StateAgg;
import java.util.ArrayList;
import java.util.List;

import static com.memfault.bort.reporting.DataType.BOOLEAN;
import static com.memfault.bort.reporting.MetricType.PROPERTY;

public class BoolStateTracker extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = BOOLEAN;
  private static final Boolean CARRY_OVER_VALUE = true;

  BoolStateTracker(String eventName, String reportType, List<StateAgg> aggregations) {
    super(eventName, reportType, aggregations, METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void state(Boolean state) {
    state(state, timestamp());
  }

  public void state(Boolean state, Long timestampMs) {
    addMetric(state, timestampMs);
  }
}
