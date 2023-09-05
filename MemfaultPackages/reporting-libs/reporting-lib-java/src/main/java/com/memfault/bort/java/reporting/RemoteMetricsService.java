package com.memfault.bort.java.reporting;

import android.os.RemoteException;
import android.util.Log;
import com.memfault.bort.internal.ILogger;
import org.json.JSONException;
import org.json.JSONObject;

import static com.memfault.bort.java.reporting.Metric.MetricJsonFields.REPORT_TYPE;
import static com.memfault.bort.java.reporting.Metric.MetricJsonFields.START_NEXT_REPORT;
import static com.memfault.bort.java.reporting.Metric.MetricJsonFields.TIMESTAMP_MS;
import static com.memfault.bort.java.reporting.Metric.MetricJsonFields.VERSION;
import static com.memfault.bort.java.reporting.RemoteLogger.CUSTOM_EVENTD_SERVICE_NAME;

class RemoteMetricsService {
  private final static String TAG = "Java-RemoteMetricsService";

  private static ILogger remoteLogger;

  private static boolean getLogger() {
    if (remoteLogger == null) {
      ILogger loggerInstance = RemoteLogger.get();
      if (loggerInstance != null) {
        remoteLogger = loggerInstance;
        return true;
      } else {
        Log.w(TAG, String.format("Unable to connect to %s", CUSTOM_EVENTD_SERVICE_NAME));
        return false;
      }
    }
    return true;
  }

  protected static void record(Metric event) {
    boolean loggerIsReady = getLogger();

    if (loggerIsReady) {
      try {
        remoteLogger.addValue(event.toJsonAndClearVals());
      } catch (RemoteException ignored) {
      } catch (JSONException e) {
        Log.w(TAG, String.format("Failed to serialize %s to json", event.eventName), e);
      }
    } else {
      Log.w(TAG,
          String.format("Logger is not ready, is memfault_structured enabled? %s was not recorded!",
              event.eventName));
    }
  }

  private static void finishReport(String name, Long endTimeMs, int version,
      Boolean startNextReport) {
    boolean loggerIsReady = getLogger();

    if (loggerIsReady) {
      try {
        remoteLogger.finishReport(
            new FinishReport(endTimeMs, version, name, startNextReport).toJson());
      } catch (RemoteException ignored) {
      } catch (JSONException e) {
        Log.w(TAG, String.format("Failed to serialize report %s to json!", name), e);
      }
    } else {
      Log.w(TAG, String.format(
          "Logger is not ready, is memfault_structured enabled? report %s will not be finished!",
          name));
    }
  }

  static class FinishReport {
    private final Long timestampMs;
    private final String reportType;
    private final Integer version;
    private final Boolean startNextReport;

    FinishReport(Long timestampMs, int version, String reportType, Boolean startNextReport) {
      this.timestampMs = timestampMs;
      this.version = version;
      this.reportType = reportType;
      this.startNextReport = startNextReport;
    }

    String toJson() throws JSONException {
      JSONObject json = new JSONObject();
      json.put(VERSION, version);
      json.put(TIMESTAMP_MS, timestampMs);
      json.put(REPORT_TYPE, reportType);

      if(startNextReport) {json.put(START_NEXT_REPORT, true); }

      return json.toString();
    }
  }
}
