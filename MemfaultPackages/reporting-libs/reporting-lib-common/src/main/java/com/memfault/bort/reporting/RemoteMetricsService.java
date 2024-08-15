package com.memfault.bort.reporting;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import com.memfault.bort.internal.ILogger;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONException;

import static java.util.Arrays.asList;

public class RemoteMetricsService {

  private static final String TAG = "RemoteMetricsService";
  private static final String AUTHORITY_CUSTOM_METRIC = "com.memfault.bort.metrics";
  private static final List<String> RESERVED_REPORT_NAMES = asList("heartbeat", "daily-heartbeat");
  private static final Pattern SESSION_NAME_REGEX =
      Pattern.compile("^[a-zA-Z0-9-_.]{1,64}$");

  public static final String HEARTBEAT_REPORT = "Heartbeat";
  public static final String SESSION_REPORT = "Session";

  public static final Uri URI_ADD_CUSTOM_METRIC =
      Uri.parse("content://" + AUTHORITY_CUSTOM_METRIC + "/add");
  public static final Uri URI_START_CUSTOM_REPORT =
      Uri.parse("content://" + AUTHORITY_CUSTOM_METRIC + "/start-report");
  public static final Uri URI_FINISH_CUSTOM_REPORT =
      Uri.parse("content://" + AUTHORITY_CUSTOM_METRIC + "/finish-report");
  public static final String KEY_CUSTOM_METRIC = "custom_metric";

  /** VisibleForTesting. */
  @SuppressLint("StaticFieldLeak")
  public static Context context = null;

  /**
   * Record the MetricValue.
   */
  public static void record(MetricValue event) {
    if (event.reportType.equals(SESSION_REPORT)) {
      String errorMessage = isSessionNameValid(event.reportName);
      if (errorMessage != null) {
        android.util.Log.w(TAG, errorMessage);
        return;
      }
    }

    boolean recorded = recordToBort(event);
    if (recorded) {
      return;
    }

    Log.w(TAG,
        String.format("Bort Metrics not available, %s falling back to structuredlogd!",
            event.eventName));
    recordToStructuredLogd(event);
  }

  private static boolean recordToStructuredLogd(MetricValue event) {
    ILogger loggerInstance = RemoteLogger.get();

    if (loggerInstance != null) {
      try {
        loggerInstance.addValue(event.toJson());
        return true;
      } catch (RemoteException | JSONException e) {
        Log.w(TAG, String.format("Failed to send metric to logger: %s", event.eventName), e);
      }
    } else {
      Log.w(TAG,
          String.format("Logger is not ready, is memfault_structured enabled? %s was not recorded!",
              event.eventName));
    }
    return false;
  }

  /**
   * Start the Report.
   */
  public static boolean startReport(StartReport startReport) {
    if (context == null) {
      android.util.Log.w(TAG, "No context to send metric: " + startReport);
      return false;
    }

    if (startReport.reportType.equals(SESSION_REPORT)) {
      String errorMessage = isSessionNameValid(startReport.reportName);
      if (errorMessage != null) {
        android.util.Log.w(TAG, errorMessage);
        return false;
      }
    }

    try {
      android.util.Log.w(TAG, "Sending start: " + startReport);
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, startReport.toJson());
      Uri result = context.getContentResolver().insert(URI_START_CUSTOM_REPORT, values);
      return result != null;
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending start: " + startReport, e);
      return false;
    }
  }

  /**
   * Finish the Report.
   */
  public static boolean finishReport(FinishReport finishReport) {
    if (context == null) {
      android.util.Log.w(TAG, "No context to send metric: " + finishReport);
      return false;
    }

    if (finishReport.reportType.equals(SESSION_REPORT)) {
      String errorMessage = isSessionNameValid(finishReport.reportName);
      if (errorMessage != null) {
        android.util.Log.w(TAG, errorMessage);
        return false;
      }
    }

    try {
      android.util.Log.w(TAG, "Sending finish: " + finishReport);
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, finishReport.toJson());
      Uri result = context.getContentResolver().insert(URI_FINISH_CUSTOM_REPORT, values);
      return result != null;
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending finish: " + finishReport, e);
      return false;
    }
  }

  /**
   * Checks whether the session name is valid, returning the error message if it isn't.
   */
  public static String isSessionNameValid(String name) {
    if (name == null) {
      return "Session name must not be null";
    }
    if (!SESSION_NAME_REGEX.matcher(name).matches()) {
      return String.format(
          "Session name [%s] must match the '${SESSION_NAME_REGEX.pattern}' regex.",
          name
      );
    }
    if (RESERVED_REPORT_NAMES.contains(name.toLowerCase(Locale.US))) {
      return String.format(
          "Session name [%s] must not be in the list of reserved names (%s).",
          name,
          RESERVED_REPORT_NAMES
      );
    }
    return null;
  }

  private static boolean recordToBort(MetricValue event) {
    if (context == null) {
      android.util.Log.w(TAG, "No context to send metric: " + event);
      return false;
    }

    try {
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, event.toJson());
      Uri result = context.getContentResolver().insert(URI_ADD_CUSTOM_METRIC, values);
      return result != null;
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending metric: " + event, e);
      return false;
    }
  }
}
