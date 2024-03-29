package com.memfault.bort.reporting;

import android.annotation.SuppressLint;
import android.os.IBinder;
import android.util.Log;
import com.memfault.bort.internal.ILogger;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Singleton provider for the remote logging service. The service gets cached
 * to avoid paying the cost of reflection on each call.
 */
final class RemoteLogger {
  private static final String TAG = "RemoteLogger";

  /**
   * The service still uses the old name for this feature: structured logging.
   */
  static final String CUSTOM_EVENTD_SERVICE_NAME = "memfault_structured";
  private static ILogger cachedLogger;

  /**
   * Obtains a logger remote proxy. This uses reflection on well-known (but hidden)
   * system APIs instead of doing extensive platform-level changes to expose the service.
   * <p>
   * Although reflection restrictions are being increasingly enforced, this specific method
   * is allowed:
   * <ul>
   *     <li>Android O: no restrictions</li>
   *     <li>Android P: part of the light-greylist</li>
   *     <li>Android Q: part of the greylist</li>
   *     <li>Android R: part of the greylist</li>
   *     <li>Android S: part of the unsupported (previously known as greylist) list</li>
   * </ul>
   * </p>
   * <p>
   * The instance is cached in cachedLogger to prevent further reflective service manager
   * queries. The cache is automatically invalidated if the service dies.
   * </p>
   *
   * @return A logger instance.
   */
  @SuppressLint("PrivateApi")
  static ILogger get() {

    if (cachedLogger != null) {
      return cachedLogger;
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

        serviceBinder.linkToDeath(new IBinder.DeathRecipient() {
          @Override
          public void binderDied() {
            cachedLogger = null;
          }
        }, 0);
        cachedLogger = ILogger.Stub.asInterface(serviceBinder);
        return cachedLogger;
      }
    } catch (ClassNotFoundException ex) {
      Log.i(TAG, "Could not find ServiceManager, please contact Memfault support.", ex);
    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
      Log.i(TAG,
          "Could not find or call ServiceManager#getService, please contact Memfault support.", ex);
    } catch (Exception ex) {
      // ignored
    }
    return null;
  }
}
