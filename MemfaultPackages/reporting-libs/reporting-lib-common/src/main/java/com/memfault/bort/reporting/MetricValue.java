package com.memfault.bort.reporting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable copy of {@link com.memfault.bort.java.reporting.Metric} for serialization.
 */
public final class MetricValue {
  public final String eventName;
  public final String reportType;
  public final List<AggregationType> aggregations;
  public final Boolean internal;
  public final MetricType metricType;
  public final DataType dataType;
  public final Boolean carryOverValue;
  public final long timeMs;
  /** Nullable. */
  public final String stringVal;
  /** Nullable. */
  public final Double numberVal;
  /** Nullable. */
  public final Boolean boolVal;
  public final int version;

  public MetricValue(
      String eventName,
      String reportType,
      List<AggregationType> aggregations,
      Boolean internal,
      MetricType metricType,
      DataType dataType,
      Boolean carryOverValue,
      long timeMs,
      /* Nullable. */
      String stringVal,
      /* Nullable. */
      Double numberVal,
      /* Nullable */
      Boolean boolVal,
      int version
  ) {
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
    this.version = version;
  }

  /**
   * Parse json.
   */
  public static MetricValue fromJson(String json) throws JSONException {
    JSONObject object = new JSONObject(json);
    // TODO handle versions here. What changed in V2?

    JSONArray aggTypesArray = object.getJSONArray(MetricJsonFields.AGGREGATIONS);
    List<AggregationType> aggTypes = new ArrayList<>();
    for (int i = 0; i < aggTypesArray.length(); i++) {
      AggregationType agg = AggregationType.fromString(aggTypesArray.getString(i));
      if (agg != null) {
        aggTypes.add(agg);
      }
    }

    String metricTypeString = object.getString(MetricJsonFields.METRIC_TYPE);
    MetricType metricType = MetricType.lookup.get(metricTypeString);
    if (metricType == null) {
      throw new JSONException("Invalid MetricType: " + metricTypeString);
    }

    String dataTypeString = object.getString(MetricJsonFields.DATA_TYPE);
    DataType dataType = DataType.lookup.get(dataTypeString);
    if (dataType == null) {
      throw new JSONException("Invalid DataType: " + dataTypeString);
    }

    String stringVal = null;
    Double doubleVal = null;
    Boolean boolVal = null;
    if (dataType == DataType.STRING) {
      stringVal = object.getString(MetricJsonFields.VALUE);
    } else if (dataType == DataType.DOUBLE) {
      doubleVal = object.getDouble(MetricJsonFields.VALUE);
    } else if (dataType == DataType.BOOLEAN) {
      boolVal = object.getString(MetricJsonFields.VALUE).equals("1");
    }

    return new MetricValue(
        object.getString(MetricJsonFields.EVENT_NAME),
        object.getString(MetricJsonFields.REPORT_TYPE),
        aggTypes,
        object.optBoolean(MetricJsonFields.INTERNAL),
        metricType,
        dataType,
        object.getBoolean(MetricJsonFields.CARRY_OVER),
        object.getLong(MetricJsonFields.TIMESTAMP_MS),
        stringVal,
        doubleVal,
        boolVal,
        object.getInt(MetricJsonFields.VERSION)
    );
  }

  String toJson() throws JSONException, IllegalArgumentException {
    JSONObject json = new JSONObject();
    json.put(MetricJsonFields.VERSION, version);
    json.put(MetricJsonFields.TIMESTAMP_MS, timeMs);
    json.put(MetricJsonFields.REPORT_TYPE, reportType);
    json.put(MetricJsonFields.EVENT_NAME, eventName);

    if (internal) {
      json.put(MetricJsonFields.INTERNAL, true);
    }

    JSONArray aggregationsJsonArray = new JSONArray();
    aggregations.forEach(agg -> {
      aggregationsJsonArray.put(agg.value());
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    MetricValue that = (MetricValue) o;
    return timeMs == that.timeMs
        && eventName.equals(that.eventName)
        && reportType.equals(that.reportType)
        && aggregations.equals(that.aggregations)
        && internal.equals(that.internal)
        && metricType == that.metricType
        && dataType == that.dataType
        && carryOverValue.equals(that.carryOverValue)
        && Objects.equals(stringVal, that.stringVal)
        && Objects.equals(numberVal, that.numberVal)
        && Objects.equals(boolVal, that.boolVal)
        && version == that.version;
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventName, reportType, aggregations, internal, metricType, dataType,
        carryOverValue, timeMs, stringVal, numberVal, boolVal, version);
  }

  @Override
  public String toString() {
    return "MetricValue{"
        + "eventName='" + eventName + '\''
        + ", reportType='" + reportType + '\''
        + ", aggregations=" + aggregations
        + ", internal=" + internal
        + ", metricType=" + metricType
        + ", dataType=" + dataType
        + ", carryOverValue=" + carryOverValue
        + ", timeMs=" + timeMs
        + ", stringVal='" + stringVal + '\''
        + ", numberVal=" + numberVal
        + ", boolVal=" + boolVal
        + ", version=" + version
        + '}';
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
