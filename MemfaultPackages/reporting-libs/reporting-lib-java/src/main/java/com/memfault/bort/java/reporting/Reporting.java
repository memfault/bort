package com.memfault.bort.java.reporting;

import com.memfault.bort.reporting.FinishReport;
import com.memfault.bort.reporting.RemoteMetricsService;
import com.memfault.bort.reporting.StartReport;

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

  /**
   * Default report. Values are aggregated using the default Bort heartbeat period.
   */
  public static Report report() {
    return new Report(HEARTBEAT_REPORT, null);
  }

  /**
   * Session. Values are aggregated by the length of each individual session with the same name.
   * <p>
   * Session names must match {@link RemoteMetricsService.SESSION_NAME_REGEX}, not be a
   * {@link RemoteMetricsService.RESERVED_REPORT_NAMES}, and not be null.
   * </p>
   */
  public static Report session(String name) {
    return new Report(SESSION_REPORT, name);
  }

  /**
   * Start a session.
   * <p>
   * Session names must match {@link RemoteMetricsService.SESSION_NAME_REGEX}, not be a
   * {@link RemoteMetricsService.RESERVED_REPORT_NAMES}, and not be null.
   * </p>
   */
  public static boolean startSession(String name) {
    return startSession(name, timestamp());
  }

  /**
   * Start a session.
   * <p>
   * Session names must match {@link RemoteMetricsService.SESSION_NAME_REGEX}, not be a
   * {@link RemoteMetricsService.RESERVED_REPORT_NAMES}, and not be null.
   * </p>
   */
  public static boolean startSession(String name, long timestampMs) {
    return RemoteMetricsService.startReport(
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
  public static boolean finishSession(String name) {
    return finishSession(name, timestamp());
  }

  /**
   * Finish a session.
   */
  public static boolean finishSession(String name, long timestampMs) {
    return RemoteMetricsService.finishReport(
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
