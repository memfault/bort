package com.memfault.bort.reporting;

import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static com.memfault.bort.reporting.NumericAgg.SUM;
import static com.memfault.bort.reporting.StateAgg.LATEST_VALUE;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetricValueTest {
  private static final long timeMs = 123456;
  private static final String reportType = "heartbeat";
  private static final int version = 3;

  @Test
  public void testStringMetric() throws JSONException {
    MetricValue metric = new MetricValue("metric_string", reportType,
        singletonList(LATEST_VALUE), false, MetricType.PROPERTY, DataType.STRING,
        true, timeMs, "abc", null, null);
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"metricType\":\"property\",\"dataType\":\"string\","
            + "\"eventName\":\"metric_string\",\"carryOver\":true,\"version\":2,"
            + "\"aggregations\":[\"LATEST_VALUE\"],\"value\":\"abc\",\"timestampMs\":123456}";
    assertEquals(expectedJson, metric.toJson());
  }

  @Test
  public void testInternalMetric() throws JSONException {
    MetricValue metric = new MetricValue("metric_string_internal", reportType,
        singletonList(LATEST_VALUE), true, MetricType.PROPERTY, DataType.STRING,
        true, timeMs, "abc", null, null);
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"metricType\":\"property\",\"internal\":true,"
            + "\"dataType\":\"string\",\"eventName\":\"metric_string_internal\",\"carryOver\":true,"
            + "\"version\":2,\"aggregations\":[\"LATEST_VALUE\"],\"value\":\"abc\","
            + "\"timestampMs\":123456}";
    assertEquals(expectedJson, metric.toJson());
  }

  @Test
  public void testNumberMetric() throws JSONException {
    MetricValue metric = new MetricValue("metric_number", reportType,
        singletonList(SUM), false, MetricType.COUNTER, DataType.DOUBLE,
        false, timeMs, null, 1.0, null);
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"metricType\":\"counter\",\"dataType\":\"double\","
            + "\"eventName\":\"metric_number\",\"carryOver\":false,\"version\":2,"
            + "\"aggregations\":[\"SUM\"],\"value\":1,\"timestampMs\":123456}";
    assertEquals(expectedJson, metric.toJson());
  }

  @Test
  public void testFinishReport() throws JSONException {
    String reportJson =
        (new RemoteMetricsService.FinishReport(timeMs, version, reportType, false)).toJson();
    String expectedJson = "{\"reportType\":\"heartbeat\",\"version\":3,\"timestampMs\":123456}";
    assertEquals(expectedJson, reportJson);
  }

  @Test
  public void testRollingFinishReport() throws JSONException {
    String reportJson =
        (new RemoteMetricsService.FinishReport(timeMs, version, reportType, true)).toJson();
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"startNextReport\":true,\"version\":3,"
            + "\"timestampMs\":123456}";
    assertEquals(expectedJson, reportJson);
  }
}
