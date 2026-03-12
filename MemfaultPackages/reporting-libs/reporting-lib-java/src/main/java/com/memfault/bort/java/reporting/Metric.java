package com.memfault.bort.java.reporting;

import android.os.SystemClock;
import com.memfault.bort.reporting.AggregationType;
import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.MetricValue;
import com.memfault.bort.reporting.RemoteMetricsService;
import java.util.Collections;
import java.util.List;

import static com.memfault.bort.reporting.MetricValue.MetricJsonFields.REPORTING_CLIENT_VERSION;

public abstract class Metric {
  final RemoteMetricsService remoteMetricsService;
  final String eventName;
  final String reportType;
  final List<? extends AggregationType> aggregations;
  final MetricType metricType;
  final DataType dataType;
  final boolean carryOverValue;
  /** Nullable. */
  final String reportName;

  Metric(RemoteMetricsService remoteMetricsService, String eventName, String reportType,
      List<? extends AggregationType> aggregations, MetricType metricType, DataType dataType,
      boolean carryOverValue, String reportName) {
    this.remoteMetricsService = remoteMetricsService;
    this.reportType = reportType;
    this.aggregations = Collections.unmodifiableList(aggregations);
    this.eventName = eventName;
    this.metricType = metricType;
    this.dataType = dataType;
    this.carryOverValue = carryOverValue;
    this.reportName = reportName;
  }

  @Deprecated
  void addMetric(String stringVal, Long timeMs) {
    addMetric(stringVal, timeMs, MetricValue.INVALID_UPTIME);
  }

  void addMetric(String stringVal, Long timeMs, Long uptimeMs) {
    dataType.verifyValueType(stringVal);
    remoteMetricsService.record(new MetricValue(
        eventName, reportType, aggregations, false, metricType, dataType, carryOverValue,
        timeMs, uptimeMs, stringVal, null, null, REPORTING_CLIENT_VERSION,
        reportName)
    );
  }

  @Deprecated
  void addMetric(Double numberVal, Long timeMs) {
    addMetric(numberVal, timeMs, MetricValue.INVALID_UPTIME);
  }

  void addMetric(Double numberVal, Long timeMs, Long uptimeMs) {
    dataType.verifyValueType(numberVal);
    remoteMetricsService.record(new MetricValue(
        eventName, reportType, aggregations, false, metricType, dataType, carryOverValue,
        timeMs, uptimeMs, null, numberVal, null, REPORTING_CLIENT_VERSION, reportName)
    );
  }

  @Deprecated
  void addMetric(Boolean boolVal, Long timeMs) {
    addMetric(boolVal, timeMs, MetricValue.INVALID_UPTIME);
  }

  void addMetric(Boolean boolVal, Long timeMs, Long uptimeMs) {
    dataType.verifyValueType(boolVal);
    remoteMetricsService.record(new MetricValue(
        eventName, reportType, aggregations, false, metricType, dataType, carryOverValue,
        timeMs, uptimeMs, null, null, boolVal, REPORTING_CLIENT_VERSION,
        reportName)
    );
  }

  static Long timestamp() {
    return System.currentTimeMillis();
  }

  static Long uptime() {
    return SystemClock.elapsedRealtime();
  }
}
