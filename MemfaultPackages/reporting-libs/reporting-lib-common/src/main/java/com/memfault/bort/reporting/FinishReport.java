package com.memfault.bort.reporting;

import android.os.SystemClock;
import org.json.JSONException;
import org.json.JSONObject;

public class FinishReport {
  public final long timestampMs;
  public final long uptimeMs;
  public final int version;
  public final String reportType;
  public final boolean startNextReport;
  public final String reportName;

  @Deprecated
  public FinishReport(long timestampMs, int version, String reportType, boolean startNextReport,
      String reportName) {
    this(timestampMs, MetricValue.INVALID_UPTIME, version, reportType, startNextReport,
        reportName);
  }

  public FinishReport(long timestampMs, long uptimeMs, int version, String reportType,
      boolean startNextReport, String reportName) {
    this.timestampMs = timestampMs;
    this.uptimeMs = uptimeMs;
    this.version = version;
    this.reportType = reportType;
    this.startNextReport = startNextReport;
    this.reportName = reportName;
  }

  /**
   * Parses JSON.
   */
  public static FinishReport fromJson(String json) throws JSONException {
    JSONObject object = new JSONObject(json);

    int version = object.getInt(MetricValue.MetricJsonFields.VERSION);
    long timestampMs = object.getLong(MetricValue.MetricJsonFields.TIMESTAMP_MS);
    long uptimeMs = object.optLong(MetricValue.MetricJsonFields.UPTIME_MS,
        MetricValue.INVALID_UPTIME);
    String reportType = object.getString(MetricValue.MetricJsonFields.REPORT_TYPE);
    boolean startNextReport =
        object.optBoolean(MetricValue.MetricJsonFields.START_NEXT_REPORT, false);

    String reportName = object.getString(MetricValue.MetricJsonFields.REPORT_NAME);

    return new FinishReport(
        timestampMs,
        uptimeMs,
        version,
        reportType,
        startNextReport,
        reportName
    );
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

    if (startNextReport) {
      json.put(MetricValue.MetricJsonFields.START_NEXT_REPORT, true);
    }

    json.put(MetricValue.MetricJsonFields.REPORT_NAME, reportName);

    return json.toString();
  }

  @Override public String toString() {
    return "FinishReport{"
        + "reportType='" + reportType + '\''
        + ", reportName='" + reportName + '\''
        + '}';
  }
}
