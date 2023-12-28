package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import java.util.Collections;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.PROPERTY;
import static com.memfault.bort.reporting.StateAgg.LATEST_VALUE;

public class StringProperty extends Metric {

  private static final MetricType METRIC_TYPE = PROPERTY;
  private static final DataType DATA_TYPE = STRING;
  private static final boolean CARRY_OVER_VALUE = true;

  StringProperty(String eventName, String reportType, boolean addLatestToReport) {
    super(eventName, reportType,
        addLatestToReport ? Collections.singletonList(LATEST_VALUE) : Collections.emptyList(),
        METRIC_TYPE, DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void update(String value) {
    update(value, timestamp());
  }

  public void update(String value, Long timestampMs) {
    addMetric(value, timestampMs);
  }
}
