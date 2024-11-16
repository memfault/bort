package com.memfault.bort.reporting;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import com.memfault.bort.internal.ILogger;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
  public static Context staticContext = null;

  private final Context context;
  private final ExecutorService executorService;

  private RemoteMetricsService(Context context, ExecutorService executorService) {
    this.context = context;
    this.executorService = executorService;
  }

  private Context getContext() {
    if (context != null) {
      return context;
    }
    return staticContext;
  }

  public static class Builder {
    private Context context;
    private ExecutorService executorService;
    private boolean immediate = false;

    public RemoteMetricsService.Builder context(Context context) {
      this.context = context;
      return this;
    }

    public RemoteMetricsService.Builder executorService(ExecutorService executorService) {
      this.executorService = executorService;
      return this;
    }

    public RemoteMetricsService.Builder immediate(boolean immediate) {
      this.immediate = immediate;
      return this;
    }

    /**
     * Builds the [RemoteMetricsService], with defaults if not provided.
     */
    public RemoteMetricsService build() {
      ExecutorService builderExecutorService = executorService;
      if (builderExecutorService == null) {
        if (immediate) {
          builderExecutorService = new ImmediateExecutorService();
        } else {
          builderExecutorService =
              Executors.newSingleThreadExecutor(Executors.defaultThreadFactory());
        }
      }
      return new RemoteMetricsService(context, builderExecutorService);
    }
  }

  private static class ImmediateExecutorService extends AbstractExecutorService {
    private boolean isShutdown = false;

    @Override public void shutdown() {
      isShutdown = true;
    }

    @Override public List<Runnable> shutdownNow() {
      isShutdown = true;
      return Collections.emptyList();
    }

    @Override public boolean isShutdown() {
      return isShutdown;
    }

    @Override public boolean isTerminated() {
      return isShutdown;
    }

    @Override public boolean awaitTermination(long timeout, TimeUnit unit) {
      return isShutdown;
    }

    @Override public void execute(Runnable command) {
      if (!isShutdown) {
        command.run();
      }
    }
  }

  /**
   * Record the MetricValue.
   */
  public CompletableFuture<Void> record(MetricValue event) {
    if (event.reportType.equals(SESSION_REPORT)) {
      String errorMessage = isSessionNameValid(event.reportName);
      if (errorMessage != null) {
        android.util.Log.w(TAG, errorMessage);
        return CompletableFuture.completedFuture(null);
      }
    }

    return CompletableFuture.supplyAsync(() -> {
      boolean recorded = recordToBort(event);
      if (recorded) {
        return null;
      }

      Log.w(TAG,
          String.format("Bort Metrics not available, %s falling back to structuredlogd!",
              event.eventName));
      recordToStructuredLogd(event);
      return null;
    }, executorService);
  }

  private void recordToStructuredLogd(MetricValue event) {
    ILogger loggerInstance = RemoteLogger.get();

    if (loggerInstance != null) {
      try {
        loggerInstance.addValue(event.toJson());
      } catch (RemoteException | JSONException e) {
        Log.w(TAG, String.format("Failed to send metric to logger: %s", event.eventName), e);
      }
    } else {
      Log.w(TAG,
          String.format("Logger is not ready, is memfault_structured enabled? %s was not recorded!",
              event.eventName));
    }
  }

  /**
   * Start the Report.
   */
  public CompletableFuture<Boolean> startReport(StartReport startReport) {
    Context context = getContext();
    if (context == null) {
      android.util.Log.w(TAG, "No context to send metric: " + startReport);
      return CompletableFuture.completedFuture(false);
    }

    if (startReport.reportType.equals(SESSION_REPORT)) {
      String errorMessage = isSessionNameValid(startReport.reportName);
      if (errorMessage != null) {
        android.util.Log.w(TAG, errorMessage);
        return CompletableFuture.completedFuture(false);
      }
    }

    return CompletableFuture.supplyAsync(() -> {
      try {
        ContentValues values = new ContentValues();
        values.put(KEY_CUSTOM_METRIC, startReport.toJson());
        ContentResolver contentResolver = context.getContentResolver();
        if (contentResolver != null) {
          Uri result = contentResolver.insert(URI_START_CUSTOM_REPORT, values);
          boolean success = result != null;
          if (!success) {
            android.util.Log.w(TAG, "Error inserting start: " + startReport);
          }
          return success;
        }
      } catch (Exception e) {
        android.util.Log.w(TAG, "Error sending start: " + startReport, e);
      }
      return false;
    }, executorService);
  }

  /**
   * Finish the Report.
   */
  public CompletableFuture<Boolean> finishReport(FinishReport finishReport) {
    Context context = getContext();
    if (context == null) {
      android.util.Log.w(TAG, "No context to send metric: " + finishReport);
      return CompletableFuture.completedFuture(false);
    }

    if (finishReport.reportType.equals(SESSION_REPORT)) {
      String errorMessage = isSessionNameValid(finishReport.reportName);
      if (errorMessage != null) {
        android.util.Log.w(TAG, errorMessage);
        return CompletableFuture.completedFuture(false);
      }
    }

    return CompletableFuture.supplyAsync(() -> {
      try {
        ContentValues values = new ContentValues();
        values.put(KEY_CUSTOM_METRIC, finishReport.toJson());
        ContentResolver contentResolver = context.getContentResolver();
        if (contentResolver != null) {
          Uri result = contentResolver.insert(URI_FINISH_CUSTOM_REPORT, values);
          boolean success = result != null;
          if (!success) {
            android.util.Log.w(TAG, "Error inserting finish: " + finishReport);
          }
          return success;
        }
      } catch (Exception e) {
        android.util.Log.w(TAG, "Error sending finish: " + finishReport, e);
      }
      return false;
    }, executorService);
  }

  public void shutdown() {
    executorService.shutdown();
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

  private boolean recordToBort(MetricValue event) {
    Context context = getContext();
    if (context == null) {
      android.util.Log.w(TAG, "No context to send metric: " + event);
      return false;
    }

    try {
      ContentValues values = new ContentValues();
      values.put(KEY_CUSTOM_METRIC, event.toJson());
      ContentResolver contentResolver = context.getContentResolver();
      if (contentResolver != null) {
        Uri result = contentResolver.insert(URI_ADD_CUSTOM_METRIC, values);
        boolean success = result != null;
        if (!success) {
          android.util.Log.w(TAG, "Error inserting metric: " + event);
        }
        return success;
      }
    } catch (Exception e) {
      android.util.Log.w(TAG, "Error sending metric: " + event, e);
    }
    return false;
  }
}
