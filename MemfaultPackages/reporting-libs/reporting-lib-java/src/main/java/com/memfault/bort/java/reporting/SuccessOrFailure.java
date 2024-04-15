package com.memfault.bort.java.reporting;

public class SuccessOrFailure {

  private final Counter successCounter;
  private final Counter failureCounter;

  SuccessOrFailure(Counter successCounter, Counter failureCounter) {
    this.successCounter = successCounter;
    this.failureCounter = failureCounter;
  }

  public void success() {
    successCounter.increment();
  }

  public void failure() {
    failureCounter.increment();
  }

  /**
   * Record a success or failure event.
   *
   * @param successful if an event was successful or failed.
   */
  public void record(boolean successful) {
    if (successful) {
      success();
    } else {
      failure();
    }
  }
}
