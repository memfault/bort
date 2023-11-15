package com.memfault.bort.reporting;

import android.os.RemoteException;
import android.util.Log;
import com.memfault.bort.internal.ILogger;
import com.memfault.bort.reporting.MetricValue.MetricJsonFields;
import org.json.JSONException;
import org.json.JSONObject;

public class RemoteMetricsService {

  private static final String TAG = "RemoteMetricsService";

  /**
   * Record the MetricValue.
   */
  public static void record(MetricValue event) {
    ILogger loggerInstance = RemoteLogger.get();

    if (loggerInstance != null) {
      try {
        loggerInstance.addValue(event.toJson());
      } catch (RemoteException | JSONException e) {
        Log.w(TAG, String.format("Failed to send metric: %s", event.eventName), e);
      }
    } else {
      Log.w(TAG,
          String.format("Logger is not ready, is memfault_structured enabled? %s was not recorded!",
              event.eventName));
    }
  }

  /**
   * Finish the HeartBeat Report. Internal-only.
   */
  public static boolean finishReport(FinishReport finishReport) {
    ILogger loggerInstance = RemoteLogger.get();

    if (loggerInstance != null) {
      try {
        loggerInstance.finishReport(finishReport.toJson());
        return true;
      } catch (RemoteException | JSONException e) {
        Log.w(TAG, String.format("Failed to finish report: %s!", finishReport.reportType), e);
      }
    } else {
      Log.w(TAG, String.format(
          "Logger is not ready, is memfault_structured enabled? report %s will not be finished!",
          finishReport.reportType));
    }
    return false;
  }

  static class FinishReport {
    final Long timestampMs;
    final String reportType;
    final Integer version;
    final Boolean startNextReport;

    FinishReport(Long timestampMs, int version, String reportType, Boolean startNextReport) {
      this.timestampMs = timestampMs;
      this.version = version;
      this.reportType = reportType;
      this.startNextReport = startNextReport;
    }

    String toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put(MetricJsonFields.VERSION, version);
      json.put(MetricJsonFields.TIMESTAMP_MS, timestampMs);
      json.put(MetricJsonFields.REPORT_TYPE, reportType);

      if (startNextReport) {
        json.put(MetricJsonFields.START_NEXT_REPORT, true);
      }

      return json.toString();
    }
  }
}
