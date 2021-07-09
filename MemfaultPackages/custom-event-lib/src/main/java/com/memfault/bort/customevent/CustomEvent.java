package com.memfault.bort.customevent;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.memfault.bort.internal.ILogger;

/**
 * Custom event logging for memfault-powered devices.
 *
 * @see #log(String, String)
 */
public final class CustomEvent {
    /**
     * The service still uses the old name for this feature: structured logging.
     */
    private static final String CUSTOM_EVENTD_SERVICE_NAME = "memfault_structured";
    private static final String LOG_TAG = "customevent";
    private static ILogger cachedLogger;

    /**
     * Obtains a logger remote proxy. This uses reflection on well-known (but hidden)
     * system APIs instead of doing extensive platform-level changes to expose the service.
     *
     *
     * Although reflection restrictions are being increasingly enforced, this specific method
     * is allowed:
     * <ul>
     *     <li>Android O: no restrictions</li>
     *     <li>Android P: part of the light-greylist</li>
     *     <li>Android Q: part of the greylist</li>
     *     <li>Android R: part of the greylist</li>
     *     <li>Android S: part of the unsupported (previously known as greylist) list</li>
     * </ul>
     *
     * The instance is cached in cachedLogger to prevent further reflective service manager
     * queries. The cache is automatically invalidated if the service dies.
     *
     * @return A logger instance.
     */
    @SuppressLint("PrivateApi")
    private static ILogger obtainRemoteLogger() {
        synchronized (CustomEvent.class) {
            if (cachedLogger != null) {
                return cachedLogger;
            }
        }

        // Non-public but stable system APIs
        try {
            Class<?> serviceManager = Class.forName("android.os.ServiceManager");
            Method getService = serviceManager.getMethod(
                    "getService",
                    String.class);
            if (getService != null) {
                IBinder serviceBinder = (IBinder) getService.invoke(
                        serviceManager,
                        CUSTOM_EVENTD_SERVICE_NAME);

                synchronized (CustomEvent.class) {
                    serviceBinder.linkToDeath(new IBinder.DeathRecipient() {
                        @Override
                        public void binderDied() {
                            synchronized (CustomEvent.class) {
                                cachedLogger = null;
                            }
                        }
                    }, 0);
                    cachedLogger = ILogger.Stub.asInterface(serviceBinder);
                    return cachedLogger;
                }
            }
        } catch (ClassNotFoundException ex) {
            new IllegalStateException("Could not find ServiceManager, please contact Memfault support.", ex)
                    .printStackTrace();
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
            new IllegalStateException("Could not find or call ServiceManager#getService, please contact Memfault support.", ex)
                    .printStackTrace();
        } catch (Exception ex) {
            // ignored
        }
        return null;
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
    public static void log(String tag, String message) {
        log(timestamp(), tag, message);
    }

    /**
     * Generate a timestamp for custom events. This currently wraps
     * {@link SystemClock#elapsedRealtimeNanos()}.
     *
    * @return The current timestamp.
     */
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
                android.util.Log.w(LOG_TAG, "Could not dump, " + CUSTOM_EVENTD_SERVICE_NAME + " not found.");
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
                android.util.Log.w(LOG_TAG, "Could not reload, " + CUSTOM_EVENTD_SERVICE_NAME + " not found.");
            } else {
                logger.reloadConfig(config);
            }
        } catch(RemoteException ex) {
            android.util.Log.w(LOG_TAG, "Could not reload", ex);
        }
    }
}
