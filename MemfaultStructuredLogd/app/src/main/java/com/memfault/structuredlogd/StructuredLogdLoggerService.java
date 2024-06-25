package com.memfault.structuredlogd;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.RemoteException;
import com.memfault.bort.internal.ILogger;

public class StructuredLogdLoggerService extends ILogger.Stub {

  public static String SERVICE_NAME = "memfault_structured";
  private static final String TAG = "StructuredLogdLoggerService";
  private static final String AUTHORITY_CUSTOM_METRIC = "com.memfault.bort.metrics";
  private static final Uri URI_ADD_CUSTOM_METRIC =
      Uri.parse("content://" + AUTHORITY_CUSTOM_METRIC + "/add");
  public static final Uri URI_START_CUSTOM_REPORT =
      Uri.parse("content://" + AUTHORITY_CUSTOM_METRIC + "/start-report");
  public static final Uri URI_FINISH_CUSTOM_REPORT =
      Uri.parse("content://" + AUTHORITY_CUSTOM_METRIC + "/finish-report");
  private static final String KEY_CUSTOM_METRIC = "custom_metric";

  private final ContentResolver contentResolver;

  public StructuredLogdLoggerService(ContentResolver contentResolver) {
    this.contentResolver = contentResolver;
  }

  /**
   * @deprecated use the Reporting library instead.
   * No-ops, instead of being deleted. The ILogger AIDL methods must be implemented for backwards
   * compatibility.
   */
  @Deprecated
  @Override public void log(long timestamp, String type, String data) throws RemoteException {
    android.util.Log.e(TAG, "ILogger.log is deprecated, ignoring: " + data);
  }

  /**
   * @deprecated use the Reporting library instead.
   * No-ops, instead of being deleted. The ILogger AIDL methods must be implemented for backwards
   * compatibility.
   */
  @Deprecated
  @Override public void logInternal(long timestamp, String type, String data)
      throws RemoteException {
    android.util.Log.e(TAG, "ILogger.logInternal is deprecated, ignoring: " + data);
  }

  /**
   * @deprecated
   * No-ops, instead of being deleted. The ILogger AIDL methods must be implemented for backwards
   * compatibility.
   */
  @Deprecated
  @Override public void triggerDump() throws RemoteException {
    android.util.Log.e(TAG, "ILogger.triggerDump is deprecated, ignoring");
  }

  /**
   * @deprecated
   * No-ops, instead of being deleted. The ILogger AIDL methods must be implemented for backwards
   * compatibility.
   */
  @Deprecated
  @Override public void reloadConfig(String config) throws RemoteException {
    android.util.Log.e(TAG, "ILogger.reloadConfig is deprecated, ignoring");
  }

  @Override
  public void startReport(String json) throws RemoteException {
    try {
      android.util.Log.v(TAG, "Sending start: " + json);
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, json);
      contentResolver.insert(URI_START_CUSTOM_REPORT, values);
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending start: " + json, e);
    }
  }

  @Override
  public void finishReport(String json) throws RemoteException {
    try {
      android.util.Log.v(TAG, "Sending finish: " + json);
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, json);
      contentResolver.insert(URI_FINISH_CUSTOM_REPORT, values);
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending finish: " + json, e);
    }
  }

  @Override
  public void addValue(String json) throws RemoteException {
    try {
      android.util.Log.v(TAG, "Sending metric: " + json);
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, json);
      contentResolver.insert(URI_ADD_CUSTOM_METRIC, values);
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending metric: " + json, e);
    }
  }
}
