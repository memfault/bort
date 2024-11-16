package com.memfault.bort.java.reporting;

import android.annotation.SuppressLint;
import com.memfault.bort.reporting.FinishReport;
import com.memfault.bort.reporting.RemoteMetricsService;
import com.memfault.bort.reporting.StartReport;
import java.util.concurrent.CompletableFuture;

import static com.memfault.bort.java.reporting.Metric.timestamp;
import static com.memfault.bort.reporting.MetricValue.MetricJsonFields.REPORTING_CLIENT_VERSION;
import static com.memfault.bort.reporting.RemoteMetricsService.HEARTBEAT_REPORT;
import static com.memfault.bort.reporting.RemoteMetricsService.SESSION_REPORT;

/**
 * Entry point to Memfault's Reporting library.
 * <p>
 * Sample usage:
 * </p>
 * <pre>
 * Counter counter = Reporting.report().counter("api-count");
 * ...
 * counter.increment();
 * </pre>
 *
 * @see <a href="https://mflt.io/android-custom-metrics">Custom Metrics</a> for more information.
 */
public class Reporting {
  @SuppressLint("StaticFieldLeak")
  private static final RemoteMetricsService remoteMetricsService =
      new RemoteMetricsService.Builder()
          .immediate(true)
          .build();

  /**
   * Default report. Values are aggregated using the default Bort heartbeat period.
   */
  public static Report report() {
    return new Report(remoteMetricsService, HEARTBEAT_REPORT, null);
  }

  /**
   * Session. Values are aggregated by the length of each individual session with the same name.
   * <p>
   * Session names must match {@link RemoteMetricsService.SESSION_NAME_REGEX}, not be a
   * {@link RemoteMetricsService.RESERVED_REPORT_NAMES}, and not be null.
   * </p>
   */
  public static Report session(String name) {
    return new Report(remoteMetricsService, SESSION_REPORT, name);
  }

  /**
   * Start a session.
   * <p>
   * Session names must match {@link RemoteMetricsService.SESSION_NAME_REGEX}, not be a
   * {@link RemoteMetricsService.RESERVED_REPORT_NAMES}, and not be null.
   * </p>
   */
  public static CompletableFuture<Boolean> startSession(String name) {
    return startSession(name, timestamp());
  }

  /**
   * Start a session.
   * <p>
   * Session names must match {@link RemoteMetricsService.SESSION_NAME_REGEX}, not be a
   * {@link RemoteMetricsService.RESERVED_REPORT_NAMES}, and not be null.
   * </p>
   */
  public static CompletableFuture<Boolean> startSession(String name, long timestampMs) {
    return remoteMetricsService.startReport(
        new StartReport(
            timestampMs,
            REPORTING_CLIENT_VERSION,
            SESSION_REPORT,
            name
        )
    );
  }

  /**
   * Finish a session.
   */
  public static CompletableFuture<Boolean> finishSession(String name) {
    return finishSession(name, timestamp());
  }

  /**
   * Finish a session.
   */
  public static CompletableFuture<Boolean> finishSession(String name, long timestampMs) {
    return remoteMetricsService.finishReport(
        new FinishReport(
            timestampMs,
            REPORTING_CLIENT_VERSION,
            SESSION_REPORT,
            /* startNextReport */ false,
            name
        )
    );
  }
}
