package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.RemoteMetricsService;
import com.memfault.bort.reporting.StateAgg;
import java.util.List;

import static com.memfault.bort.reporting.DataType.BOOLEAN;
import static com.memfault.bort.reporting.MetricType.PROPERTY;

public class BoolStateTracker extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = BOOLEAN;
  private static final boolean CARRY_OVER_VALUE = true;

  BoolStateTracker(RemoteMetricsService remoteMetricsService, String eventName, String reportType,
      List<StateAgg> aggregations, String reportName) {
    super(remoteMetricsService, eventName, reportType, aggregations, METRIC_TYPE, DATA_TYPE,
        CARRY_OVER_VALUE, reportName);
  }

  public void state(Boolean state) {
    state(state, timestamp());
  }

  public void state(Boolean state, Long timestampMs) {
    addMetric(state, timestampMs);
  }
}
