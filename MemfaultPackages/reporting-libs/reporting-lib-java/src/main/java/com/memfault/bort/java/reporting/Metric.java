package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class Metric {
  final String eventName;
  final String reportType;
  final ArrayList aggregations;
  final Boolean internal;
  final MetricType metricType;
  final DataType dataType;
  final Boolean carryOverValue;
  Long timeMs;
  String stringVal = null;
  Double numberVal = null;
  Boolean boolVal = null;

  Metric(String eventName, String reportType, ArrayList aggregations,
      Boolean internal, MetricType metricType, DataType dataType, Boolean carryOverValue) {
    this.reportType = reportType;
    this.aggregations = aggregations;
    this.eventName = eventName;
    this.internal = internal;
    this.metricType = metricType;
    this.dataType = dataType;
    this.carryOverValue = carryOverValue;
  }

  Metric(String eventName, String reportType, Boolean internal, MetricType metricType,
      DataType dataType, Boolean carryOverValue) {
    this.reportType = reportType;
    aggregations = new ArrayList<>();
    this.eventName = eventName;
    this.internal = internal;
    this.metricType = metricType;
    this.dataType = dataType;
    this.carryOverValue = carryOverValue;
  }

  synchronized void addMetric(String stringVal, Long timeMs) {
    dataType.verifyValueType(stringVal);
    this.timeMs = timeMs;
    this.stringVal = stringVal;

    RemoteMetricsService.record(this);
  }

  synchronized void addMetric(Double numberVal, Long timeMs) {
    dataType.verifyValueType(numberVal);
    this.timeMs = timeMs;
    this.numberVal = numberVal;

    RemoteMetricsService.record(this);
  }

  synchronized void addMetric(Boolean boolVal, Long timeMs) {
    dataType.verifyValueType(boolVal);
    this.timeMs = timeMs;
    this.boolVal = boolVal;

    RemoteMetricsService.record(this);
  }

  private void clearVals() {
    this.boolVal = null;
    this.numberVal = null;
    this.stringVal = null;
  }

  Long timestamp() {
    return System.currentTimeMillis();
  }

  String toJsonAndClearVals() throws JSONException, IllegalArgumentException {
    JSONObject json = new JSONObject();
    json.put(MetricJsonFields.VERSION, MetricJsonFields.REPORTING_CLIENT_VERSION);
    json.put(MetricJsonFields.TIMESTAMP_MS, timeMs);
    json.put(MetricJsonFields.REPORT_TYPE, reportType);
    json.put(MetricJsonFields.EVENT_NAME, eventName);

    if (internal) {
      json.put(MetricJsonFields.INTERNAL, true);
    }

    JSONArray aggregationsJsonArray = new JSONArray();
    aggregations.forEach(agg -> {
      aggregationsJsonArray.put(agg.toString().toUpperCase());
    });
    json.put(MetricJsonFields.AGGREGATIONS, aggregationsJsonArray);

    if (stringVal != null) {
      json.put(MetricJsonFields.VALUE, stringVal);
    } else if (numberVal != null) {
      json.put(MetricJsonFields.VALUE, numberVal);
    } else if (boolVal != null) {
      json.put(MetricJsonFields.VALUE, (boolVal ? "1" : "0"));
    } else {
      throw new IllegalArgumentException("Expected a value to not be null");
    }
    clearVals();

    json.put(MetricJsonFields.METRIC_TYPE, metricType.value);
    json.put(MetricJsonFields.DATA_TYPE, dataType.value);
    json.put(MetricJsonFields.CARRY_OVER, carryOverValue);

    return json.toString();
  }

  public static class MetricJsonFields {
    public final static int REPORTING_CLIENT_VERSION = 2;

    /**
     * Bump this when the schema changes.
     */
    protected static final String VERSION = "version";
    protected static final String TIMESTAMP_MS = "timestampMs";
    protected static final String REPORT_TYPE = "reportType";
    protected static final String START_NEXT_REPORT = "startNextReport";
    protected static final String EVENT_NAME = "eventName";
    protected static final String INTERNAL = "internal";
    protected static final String AGGREGATIONS = "aggregations";
    protected static final String VALUE = "value";
    protected static final String METRIC_TYPE = "metricType";
    protected static final String DATA_TYPE = "dataType";
    protected static final String CARRY_OVER = "carryOver";
  }
}
