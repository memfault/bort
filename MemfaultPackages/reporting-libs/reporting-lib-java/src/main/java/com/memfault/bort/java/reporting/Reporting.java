package com.memfault.bort.java.reporting;

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
  private static final String HEARTBEAT_REPORT = "Heartbeat";

  /**
   * Default report. Values are aggregated using the default Bort heartbeat period.
   */
  public static Report report() {
    return new Report(HEARTBEAT_REPORT);
  }
}
