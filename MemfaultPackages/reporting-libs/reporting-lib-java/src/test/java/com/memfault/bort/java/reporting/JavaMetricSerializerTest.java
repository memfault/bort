package com.memfault.bort.java.reporting;

import java.util.ArrayList;
import org.json.JSONException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JavaMetricSerializerTest {
  private static final long timeMs = 123456;
  private static final String reportType = "heartbeat";
  private static final int version = 3;

  private <T extends Metric> T testMetric(Class<T> clazz, String metricName, Object value,
      boolean internal) {
    Metric metric;
    if (clazz.equals(BoolStateTracker.class)) {
      metric = new BoolStateTracker(metricName, reportType, new ArrayList<>(), internal);
      ((BoolStateTracker) metric).state((Boolean) value, timeMs);
    } else if (clazz.equals(Counter.class)) {
      metric = new Counter(metricName, reportType, true, internal);
      ((Counter) metric).incrementBy((Integer) value, timeMs);
    } else if (clazz.equals(StringProperty.class)) {
      metric = new StringProperty(metricName, reportType, true, internal);
      ((StringProperty) metric).update((String) value, timeMs);
    } else {
      throw new UnsupportedOperationException(
          String.format("%s MetricType not handled in switch statement", clazz.getName()));
    }

    return (T) metric;
  }

  @Test
  public void testStringMetric() throws JSONException {
    Metric
        metric = testMetric(StringProperty.class, "metric_string", "abc", false);
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"metricType\":\"property\",\"dataType\":\"string\",\"eventName\":\"metric_string\",\"carryOver\":true,\"version\":2,\"aggregations\":[\"LATEST_VALUE\"],\"value\":\"abc\",\"timestampMs\":123456}";

    assertEquals(expectedJson, metric.toJsonAndClearVals());
  }

  @Test
  public void testInternalMetric() throws JSONException {
    Metric
        metric = testMetric(StringProperty.class, "metric_string_internal", "abc", true);
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"metricType\":\"property\",\"internal\":true,\"dataType\":\"string\",\"eventName\":\"metric_string_internal\",\"carryOver\":true,\"version\":2,\"aggregations\":[\"LATEST_VALUE\"],\"value\":\"abc\",\"timestampMs\":123456}";
    assertEquals(expectedJson, metric.toJsonAndClearVals());
  }

  @Test
  public void testNumberMetric() throws JSONException {
    Metric metric = testMetric(Counter.class, "metric_number", 1, false);
    String expectedJson =
        "{\"reportType\":\"heartbeat\",\"metricType\":\"counter\",\"dataType\":\"double\",\"eventName\":\"metric_number\",\"carryOver\":false,\"version\":2,\"aggregations\":[\"SUM\"],\"value\":1,\"timestampMs\":123456}";
    assertEquals(expectedJson, metric.toJsonAndClearVals());
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
        "{\"reportType\":\"heartbeat\",\"startNextReport\":true,\"version\":3,\"timestampMs\":123456}";
    assertEquals(expectedJson, reportJson);
  }
}
