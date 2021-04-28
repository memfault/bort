package com.memfault.bort.internal;

/** {@hide} */
interface ILogger {
  /**
   * Add a structured log entry.
   * @param timestamp nanoseconds since boot
   * @param type freeform name for the type of data being logged, types are expected (but not obliged to) follow similar
   *             data structures.
   * @param data free-form JSON data, invalid JSON is taken in but wrapped in an error type.
   */
  oneway void log(long timestamp, String type, String data);

  /**
   * Add an internal structured log entry, these have special meaning and are not meant to be used
   * by apps or external users.
   *
   * @param timestamp nanoseconds since boot
   * @param type freeform name for the type of data being logged, types are expected (but not obliged to) follow similar
   *             data structures.
   * @param data free-form JSON data, invalid JSON is taken in but wrapped in an error type.
   */
  oneway void logInternal(long timestamp, String type, String data);

  /**
   * Trigger a structured log dump. This is used for testing and is a no-op in user builds.
   */
  oneway void triggerDump();
}
