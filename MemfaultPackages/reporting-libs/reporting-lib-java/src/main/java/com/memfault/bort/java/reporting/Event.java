package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.AggregationType;
import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.StateAgg;
import java.util.ArrayList;
import java.util.List;

import static com.memfault.bort.reporting.DataType.STRING;
import static com.memfault.bort.reporting.MetricType.EVENT;
import static com.memfault.bort.reporting.NumericAgg.COUNT;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

public class Event extends Metric {

  private static final MetricType METRIC_TYPE = EVENT;
  private static final DataType DATA_TYPE = STRING;
  private static final boolean CARRY_OVER_VALUE = false;

  Event(String eventName, String reportType, boolean countInReport, boolean latestInReport) {
    super(eventName, reportType,
        Event.<AggregationType>union(countInReport ? singletonList(COUNT) : emptyList(),
            latestInReport ? singletonList(StateAgg.LATEST_VALUE) : emptyList()),
        METRIC_TYPE,
        DATA_TYPE, CARRY_OVER_VALUE);
  }

  public void add(String value) {
    add(value, timestamp());
  }

  public void add(String value, Long timestampMs) {
    addMetric(value, timestampMs);
  }

  private static <T> List<T> union(List<T> a, List<T> b) {
    List<T> c = new ArrayList<>(a);
    c.addAll(b);
    return c;
  }
}
