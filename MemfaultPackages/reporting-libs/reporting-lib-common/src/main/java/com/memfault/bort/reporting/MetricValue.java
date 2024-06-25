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
  /** Nullable. */
  public final String reportName;

  public MetricValue(
      String eventName,
      String reportType,
      List<? extends AggregationType> aggregations,
      Boolean internal,
      MetricType metricType,
      DataType dataType,
      Boolean carryOverValue,
      long timeMs,
      /* Nullable. */
      String stringVal,
      /* Nullable. */
      Double numberVal,
      /* Nullable. */
      Boolean boolVal,
      int version,
      /* Nullable. */
      String reportName
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
    this.reportName = reportName;
  }

  private static boolean hasBool(JSONObject object, String field) {
    if (object.has(field)) {
      try {
        object.getBoolean(field);
        return true;
      } catch (JSONException jsonException) {
        return false;
      }
    }
    return false;
  }

  private static boolean hasLong(JSONObject object, String field) {
    if (object.has(field)) {
      try {
        object.getLong(field);
        return true;
      } catch (JSONException jsonException) {
        return false;
      }
    }
    return false;
  }

  private static boolean hasDouble(JSONObject object, String field) {
    if (object.has(field)) {
      try {
        object.getDouble(field);
        return true;
      } catch (JSONException jsonException) {
        return false;
      }
    }
    return false;
  }

  private static boolean hasString(JSONObject object, String field) {
    if (object.has(field)) {
      try {
        object.getString(field);
        return true;
      } catch (JSONException jsonException) {
        return false;
      }
    }
    return false;
  }

  private static boolean hasStringArray(JSONObject object, String field) {
    if (object.has(field)) {
      try {
        JSONArray jsonArray = object.getJSONArray(field);
        boolean isStringArray = true;
        for (int i = 0; i < jsonArray.length(); i++) {
          try {
            jsonArray.getString(i);
          } catch (JSONException jsonException) {
            isStringArray = false;
          }
        }
        return isStringArray;
      } catch (JSONException jsonException) {
        return false;
      }
    }
    return false;
  }

  private static boolean isCompliantV1(JSONObject object) {
    return hasLong(object, MetricJsonFields.TIMESTAMP_MS)
        && hasString(object, MetricJsonFields.REPORT_TYPE)
        && hasString(object, MetricJsonFields.EVENT_NAME)
        && (!object.has(MetricJsonFields.INTERNAL) || hasBool(object, MetricJsonFields.INTERNAL))
        && hasStringArray(object, MetricJsonFields.AGGREGATIONS)
        && (hasBool(object, MetricJsonFields.VALUE)
        || hasDouble(object, MetricJsonFields.VALUE)
        || hasString(object, MetricJsonFields.VALUE));
  }

  private static boolean isCompliantV2(JSONObject object) {
    return isCompliantV1(object)
        && hasString(object, MetricJsonFields.DATA_TYPE)
        && hasString(object, MetricJsonFields.METRIC_TYPE)
        && hasBool(object, MetricJsonFields.CARRY_OVER);
  }

  /**
   * Parse json.
   */
  public static MetricValue fromJson(String json) throws JSONException {
    JSONObject object = new JSONObject(json);

    int version = object.getInt(MetricJsonFields.VERSION);

    if (version >= 2) {
      if (!isCompliantV2(object)) {
        throw new JSONException("MetricValue is not V2 compliant: " + json);
      }

      return fromJsonV2(object);
    }

    if (!isCompliantV1(object)) {
      throw new JSONException("MetricValue is not V1 compliant: " + json);
    }
    return fromJsonV1(object);
  }

  private static MetricValue fromJsonV1(JSONObject object) throws JSONException {
    JSONArray aggTypesArray = object.getJSONArray(MetricJsonFields.AGGREGATIONS);
    List<AggregationType> aggTypes = new ArrayList<>();
    for (int i = 0; i < aggTypesArray.length(); i++) {
      AggregationType agg = AggregationType.fromString(aggTypesArray.getString(i));
      if (agg != null) {
        aggTypes.add(agg);
      }
    }

    String stringVal = null;
    Double doubleVal = null;
    Boolean boolVal = null;
    DataType dataType;
    if (hasBool(object, MetricJsonFields.VALUE)) {
      dataType = DataType.BOOLEAN;
      boolVal = object.getBoolean(MetricJsonFields.VALUE);
    } else if (hasDouble(object, MetricJsonFields.VALUE)) {
      dataType = DataType.DOUBLE;
      doubleVal = object.getDouble(MetricJsonFields.VALUE);
    } else if (hasString(object, MetricJsonFields.VALUE)) {
      dataType = DataType.STRING;
      stringVal = object.getString(MetricJsonFields.VALUE);
    } else {
      throw new JSONException("Invalid DataType: " + object);
    }

    // We could improve the logic here, but hopefully this will push users to update their
    // libraries instead.
    MetricType metricType;
    if (aggTypes.contains(NumericAgg.COUNT)) {
      metricType = MetricType.COUNTER;
    } else if (aggTypes.contains(NumericAgg.MIN)
        || aggTypes.contains(NumericAgg.MEAN)
        || aggTypes.contains(NumericAgg.MAX)
        || aggTypes.contains(NumericAgg.SUM)) {
      metricType = MetricType.GAUGE;
    } else {
      metricType = MetricType.PROPERTY;
    }

    return new MetricValue(
        object.getString(MetricJsonFields.EVENT_NAME),
        object.getString(MetricJsonFields.REPORT_TYPE),
        aggTypes,
        object.optBoolean(MetricJsonFields.INTERNAL),
        metricType,
        dataType,
        /* carryOver */ false,
        object.getLong(MetricJsonFields.TIMESTAMP_MS),
        stringVal,
        doubleVal,
        boolVal,
        object.getInt(MetricJsonFields.VERSION),
        /* reportName */ null
    );
  }

  private static MetricValue fromJsonV2(JSONObject object) throws JSONException {
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
      boolVal = object.getString(MetricJsonFields.VALUE).equals("1")
          || object.optBoolean(MetricJsonFields.VALUE);
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
        object.getInt(MetricJsonFields.VERSION),
        object.has(MetricJsonFields.REPORT_NAME)
            ? object.getString(MetricJsonFields.REPORT_NAME) : null
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
    json.put(MetricJsonFields.REPORT_NAME, reportName);

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
        && version == that.version
        && Objects.equals(reportName, that.reportName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(eventName, reportType, aggregations, internal, metricType, dataType,
        carryOverValue, timeMs, stringVal, numberVal, boolVal, version, reportName);
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
        + ", reportName='" + reportName + '\''
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
    static final String REPORT_NAME = "reportName";
  }
}
