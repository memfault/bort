package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.DataType;
import com.memfault.bort.reporting.MetricType;
import com.memfault.bort.reporting.MetricValue;
import com.memfault.bort.reporting.NumericAgg;
import com.memfault.bort.reporting.RemoteMetricsService;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.memfault.bort.reporting.StateAgg.LATEST_VALUE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;

public class JavaMetricTest {
  private static final long timeMs = 123456;
  private static final String reportType = "heartbeat";

  private <T extends Metric> void testMetric(Class<T> clazz, String metricName, Object value) {
    Metric metric;
    if (clazz.equals(BoolStateTracker.class)) {
      metric = new BoolStateTracker(metricName, reportType, new ArrayList<>());
      ((BoolStateTracker) metric).state((Boolean) value, timeMs);
    } else if (clazz.equals(Counter.class)) {
      metric = new Counter(metricName, reportType, true);
      ((Counter) metric).incrementBy((Integer) value, timeMs);
    } else if (clazz.equals(StringProperty.class)) {
      metric = new StringProperty(metricName, reportType, true);
      ((StringProperty) metric).update((String) value, timeMs);
    } else {
      throw new UnsupportedOperationException(
          String.format("%s MetricType not handled in switch statement", clazz.getName()));
    }

    T t = (T) metric;
  }

  @Test public void testStringMetric() {
    ArgumentCaptor<MetricValue> argument = ArgumentCaptor.forClass(MetricValue.class);
    try (MockedStatic<RemoteMetricsService> service = Mockito.mockStatic(
        RemoteMetricsService.class)) {
      testMetric(StringProperty.class, "metric_string", "abc");
      service.verify(() -> RemoteMetricsService.record(argument.capture()), times(1));
      MetricValue result = argument.getValue();
      assertEquals("metric_string", result.eventName);
      assertEquals(MetricType.PROPERTY, result.metricType);
      assertEquals(DataType.STRING, result.dataType);
      assertEquals(1, result.aggregations.size());
      assertEquals(LATEST_VALUE, result.aggregations.get(0));
      assertEquals(true, result.carryOverValue);
      assertEquals(reportType, result.reportType);
      assertEquals(timeMs, result.timeMs);
      assertEquals("abc", result.stringVal);
      assertEquals(null, result.numberVal);
      assertEquals(null, result.boolVal);
    }
  }

  @Test public void testNumberMetric() {
    ArgumentCaptor<MetricValue> argument = ArgumentCaptor.forClass(MetricValue.class);
    try (MockedStatic<RemoteMetricsService> service = Mockito.mockStatic(
        RemoteMetricsService.class)) {
      testMetric(Counter.class, "metric_number", 1);
      service.verify(() -> RemoteMetricsService.record(argument.capture()), times(1));
      MetricValue result = argument.getValue();
      assertEquals("metric_number", result.eventName);
      assertEquals(MetricType.COUNTER, result.metricType);
      assertEquals(DataType.DOUBLE, result.dataType);
      assertEquals(1, result.aggregations.size());
      assertEquals(NumericAgg.SUM, result.aggregations.get(0));
      assertEquals(false, result.carryOverValue);
      assertEquals(reportType, result.reportType);
      assertEquals(timeMs, result.timeMs);
      assertEquals(null, result.stringVal);
      assertEquals(1, result.numberVal);
      assertEquals(null, result.boolVal);
    }
  }
}
