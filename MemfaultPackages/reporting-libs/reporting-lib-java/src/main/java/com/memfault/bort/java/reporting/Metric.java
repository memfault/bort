package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.AggregationType;
import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.MetricValue;
import com.memfault.bort.reporting.RemoteMetricsService;
import java.util.Collections;
import java.util.List;

public abstract class Metric {
  final String eventName;
  final String reportType;
  final List<? extends AggregationType> aggregations;
  final MetricType metricType;
  final DataType dataType;
  final Boolean carryOverValue;

  Metric(String eventName, String reportType, List<? extends AggregationType> aggregations,
      MetricType metricType, DataType dataType, Boolean carryOverValue) {
    this.reportType = reportType;
    this.aggregations = Collections.unmodifiableList(aggregations);
    this.eventName = eventName;
    this.metricType = metricType;
    this.dataType = dataType;
    this.carryOverValue = carryOverValue;
  }

  void addMetric(String stringVal, Long timeMs) {
    dataType.verifyValueType(stringVal);
    RemoteMetricsService.record(new MetricValue(
        eventName, reportType, aggregations, false, metricType, dataType, carryOverValue,
        timeMs, stringVal, null, null)
    );
  }

  void addMetric(Double numberVal, Long timeMs) {
    dataType.verifyValueType(numberVal);
    RemoteMetricsService.record(new MetricValue(
        eventName, reportType, aggregations, false, metricType, dataType, carryOverValue,
        timeMs, null, numberVal, null)
    );
  }

  void addMetric(Boolean boolVal, Long timeMs) {
    dataType.verifyValueType(boolVal);
    RemoteMetricsService.record(new MetricValue(
        eventName, reportType, aggregations, false, metricType, dataType, carryOverValue,
        timeMs, null, null, boolVal)
    );
  }

  static Long timestamp() {
    return System.currentTimeMillis();
  }
}
