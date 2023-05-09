package com.memfault.bort.reporting;

import android.os.RemoteException;
import android.os.SystemClock;

import com.memfault.bort.internal.ILogger;
import kotlin.Deprecated;

/**
 * Custom event logging for memfault-powered devices.
 *
 * @see #log(String, String)
 */
@Deprecated(message = "Use the Reporting.report().event() api.")
public final class CustomEvent {
    private static final String LOG_TAG = "customevent";

    /**
     * Obtains an instance of the remote logger.
     */
    private static ILogger obtainRemoteLogger() {
        return RemoteLogger.get();
    }

    /**
     * Write a custom event with a specific timestamp. Prefer using
     * {@link CustomEvent#log(String, String)} if you have no interest in providing manual
     * timestamp data. Use-cases for manual timestamp data include deferred logging, if that's the
     * case, obtain a timestamp with {@link #timestamp()} and keep it for later usage.
     *
     * @see #log(String, String)
     * @param timestamp A timestamp containing elapsed nanoseconds since boot, prefer using
     *                  {@link #timestamp()} to obtain it.
     * @param tag A contextual tag for the message
     * @param message A free-form JSON structure, invalid JSON will fail silently and be ignored.
     */
    @Deprecated(message = "Use the Reporting.report().event() api.")
    public static void log(long timestamp, String tag, String message) {
        try {
            ILogger logger = obtainRemoteLogger();
            if (logger == null) {
                // If no logger, write to the system log. Timestamp is lost.
                android.util.Log.w(tag, message);
            } else{
                logger.log(timestamp, tag, message);
            }
        } catch(RemoteException ex) {
            android.util.Log.w("customevent", "Could not write log", ex);
        }
    }

    /**
     * Write a custom event.
     *
     * @param tag A contextual tag for the message
     * @param message A free-form JSON structure, while you can provide invalid JSON, it will be
     *                wrapped in an error-typed structure and JSON will be escaped.
     */
    @Deprecated(message = "Use the Reporting.report().event() api.")
    public static void log(String tag, String message) {
        log(timestamp(), tag, message);
    }

    /**
     * Generate a timestamp for custom events. This currently wraps
     * {@link SystemClock#elapsedRealtimeNanos()}.
     *
    * @return The current timestamp.
     */
    @Deprecated(message = "Use the Reporting.report().event() api.")
    public static long timestamp() {
        return SystemClock.elapsedRealtimeNanos();
    }

    /**
     * Dumps log files into DropBox. This method is used for integration testing and does not
     * work in production builds.
     */
    public static void dump() {
        try {
            ILogger logger = obtainRemoteLogger();
            if (logger == null) {
                android.util.Log.w(LOG_TAG, "Could not dump, "
                        + RemoteLogger.CUSTOM_EVENTD_SERVICE_NAME + " not found.");
            } else {
                logger.triggerDump();
            }
        } catch(RemoteException ex) {
            android.util.Log.w(LOG_TAG, "Could not dump", ex);
        }
    }

    /**
     * Reloads the custom event config. Do not use in apps, it will throw a security exception.
     * @param config The configuration JSON as a string.
     */
    public static void reloadConfig(String config) {
        try {
            ILogger logger = obtainRemoteLogger();
            if (logger == null) {
                android.util.Log.w(LOG_TAG, "Could not reload, "
                        + RemoteLogger.CUSTOM_EVENTD_SERVICE_NAME + " not found.");
            } else {
                logger.reloadConfig(config);
            }
        } catch(RemoteException ex) {
            android.util.Log.w(LOG_TAG, "Could not reload", ex);
        }
    }
}
