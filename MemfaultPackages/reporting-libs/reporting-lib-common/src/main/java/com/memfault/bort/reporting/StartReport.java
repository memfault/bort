package com.memfault.bort.reporting;

import android.os.SystemClock;
import org.json.JSONException;
import org.json.JSONObject;

public class StartReport {
  public final long timestampMs;
  public final long uptimeMs;
  public final int version;
  public final String reportType;
  public final String reportName;

  public StartReport(long timestampMs, long uptimeMs, int version, String reportType,
      String reportName) {
    this.timestampMs = timestampMs;
    this.uptimeMs = uptimeMs;
    this.version = version;
    this.reportType = reportType;
    this.reportName = reportName;
  }

  /**
   * Parses JSON.
   */
  public static StartReport fromJson(String json) throws JSONException {
    JSONObject object = new JSONObject(json);

    int version = object.getInt(MetricValue.MetricJsonFields.VERSION);
    long timestampMs = object.getLong(MetricValue.MetricJsonFields.TIMESTAMP_MS);
    long uptimeMs = object.optLong(MetricValue.MetricJsonFields.UPTIME_MS,
        MetricValue.INVALID_UPTIME);
    String reportType = object.getString(MetricValue.MetricJsonFields.REPORT_TYPE);
    String reportName = object.getString(MetricValue.MetricJsonFields.REPORT_NAME);

    return new StartReport(timestampMs, uptimeMs, version, reportType, reportName);
  }

  /**
   * Writes JSON.
   */
  public String toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put(MetricValue.MetricJsonFields.VERSION, version);
    json.put(MetricValue.MetricJsonFields.TIMESTAMP_MS, timestampMs);
    json.put(MetricValue.MetricJsonFields.UPTIME_MS, uptimeMs);
    json.put(MetricValue.MetricJsonFields.REPORT_TYPE, reportType);
    json.put(MetricValue.MetricJsonFields.REPORT_NAME, reportName);
    return json.toString();
  }

  @Override public String toString() {
    return "StartReport{"
        + "reportType='" + reportType + '\''
        + ", reportName='" + reportName + '\''
        + '}';
  }
}
