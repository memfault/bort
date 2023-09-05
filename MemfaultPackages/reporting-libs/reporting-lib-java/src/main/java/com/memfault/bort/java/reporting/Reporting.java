package com.memfault.bort.java.reporting;

public class Reporting {
  static final String HEARTBEAT_REPORT = "Heartbeat";

  /**
   * Default report. Values are aggregated using the default Bort heartbeat period.
   */
  public static Report report() {
    return new Report(HEARTBEAT_REPORT);
  }
}
