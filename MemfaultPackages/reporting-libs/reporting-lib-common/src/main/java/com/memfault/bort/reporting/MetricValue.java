package com.memfault.bort.reporting;

import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable copy of {@link com.memfault.bort.java.reporting.Metric} for serialization.
 */
public final class MetricValue {
  public final String eventName;
  public final String reportType;
  public final List<? extends AggregationType> aggregations;
  public final Boolean internal;
  public final MetricType metricType;
  public final DataType dataType;
  public final Boolean carryOverValue;
  public final long timeMs;
  public final String stringVal;
  public final Double numberVal;
  public final Boolean boolVal;

  public MetricValue(String eventName, String reportType,
      List<? extends AggregationType> aggregations, Boolean internal, MetricType metricType,
      DataType dataType, Boolean carryOverValue, long timeMs, String stringVal, Double numberVal,
      Boolean boolVal) {
    this.reportType = reportType;
    this.aggregations = Collections.unmodifiableList(aggregations);
    this.eventName = eventName;
    this.internal = internal;
    this.metricType = metricType;
    this.dataType = dataType;
    this.carryOverValue = carryOverValue;
    this.timeMs = timeMs;
    this.stringVal = stringVal;
    this.numberVal = numberVal;
    this.boolVal = boolVal;
  }

  String toJson() throws JSONException, IllegalArgumentException {
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

    json.put(MetricJsonFields.METRIC_TYPE, metricType.value);
    json.put(MetricJsonFields.DATA_TYPE, dataType.value);
    json.put(MetricJsonFields.CARRY_OVER, carryOverValue);

    return json.toString();
  }

  public static class MetricJsonFields {
    public static final int REPORTING_CLIENT_VERSION = 2;

    static final String VERSION = "version";
    static final String TIMESTAMP_MS = "timestampMs";
    static final String REPORT_TYPE = "reportType";
    static final String START_NEXT_REPORT = "startNextReport";
    static final String EVENT_NAME = "eventName";
    static final String INTERNAL = "internal";
    static final String AGGREGATIONS = "aggregations";
    static final String VALUE = "value";
    static final String METRIC_TYPE = "metricType";
    static final String DATA_TYPE = "dataType";
    static final String CARRY_OVER = "carryOver";
  }
}
