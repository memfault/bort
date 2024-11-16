package com.memfault.structuredlogd;

import android.app.Application;
import android.content.ContentResolver;
import android.os.ServiceManager;
import android.util.Log;

import static com.memfault.structuredlogd.StructuredLogdLoggerService.SERVICE_NAME;

public class MemfaultStructuredLogdApp extends Application {
  private static final String TAG = "MemfaultStructuredLogdApp";

  @Override public void onCreate() {
    super.onCreate();
    ContentResolver contentResolver = getContentResolver();
    StructuredLogdLoggerService service = new StructuredLogdLoggerService(contentResolver);
    try {
      ServiceManager.addService(SERVICE_NAME, service);
      Log.i(TAG, "MemfaultStructuredLogd ready");
    } catch (SecurityException e) {
      Log.e(TAG, "MemfaultStructuredLogd service could not be registered. Backwards "
          + "compatibility with the reporting-lib < 1.5 is not guaranteed. This usually indicates"
          + "an integration issue. Please re-run the Bort CLI validation tool "
          + "(bort_cli.py validate-sdk-integration) and ensure the sepolicy definitions are"
          + "properly included in your build.", e);
      service.addValue(structuredLogdSepolicyErrorJson());
    }
  }

  private String structuredLogdSepolicyErrorJson() {
    long timestampMs = System.currentTimeMillis();
    return "{\"version\":2,\"timestampMs\":" + timestampMs + ",\"reportType\":\"Heartbeat\","
        + "\"eventName\":\"structuredlogd_se_error\",\"aggregations\":[\"SUM\"],"
        + "\"value\":1,\"metricType\":\"counter\",\"dataType\":\"double\",\"carryOver\":false,"
        + "\"internal\":true}";
  }
}
