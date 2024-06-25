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
    ServiceManager.addService(SERVICE_NAME, new StructuredLogdLoggerService(contentResolver));
    Log.i(TAG, "MemfaultStructuredLogd ready");
  }
}
