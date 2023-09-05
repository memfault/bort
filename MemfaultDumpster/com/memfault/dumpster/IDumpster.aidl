package com.memfault.dumpster;

import android.os.PersistableBundle;
import com.memfault.dumpster.IDumpsterBasicCommandListener;

interface IDumpster {
    const int VERSION_INITIAL = 1;
    const int VERSION_BORT_ENABLED_PROPERTY = 2;
    const int VERSION_GETPROP_TYPES = 3;
    const int VERSION_BORT_CONTINUOUS_LOGGING = 4;
    const int VERSION_BORT_CONTINUOUS_FIXES = 5;

    /**
     * Current version of the service.
     */
    const int VERSION = 5;

    /**
    * Gets the version of the MemfaultDumpster service.
    */
    int getVersion() = 0;

    const int CMD_ID_GETPROP = 1;
    const int CMD_ID_SET_BORT_ENABLED_PROPERTY_ENABLED = 2;
    const int CMD_ID_SET_BORT_ENABLED_PROPERTY_DISABLED = 3;
    const int CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_ENABLED = 4;
    const int CMD_ID_SET_STRUCTURED_ENABLED_PROPERTY_DISABLED = 5;
    const int CMD_ID_GETPROP_TYPES = 6;

    /**
     * Runs a basic command and calls the listener with the string output.
     *
     * @param cmdId One of the CMD_ID_...s, see constants above.
     * @param listener callback that will receive the result of the call.
     */
    oneway void runBasicCommand(int cmdId, IDumpsterBasicCommandListener listener) = 1;

    /**
     * Starts continuous logging with a set of options. Options are intentionally opaque
     * so that they can be versioned and augmented. If continuous logging is already
     * running, the logger is reconfigured with the contents of the bundle.
     * Version 0:
     *  - int version (always 0 for this version)
     *  - List<String> filterSpecs (a list of specs that should be applied to logcat)
     *  - int dumpThresholdBytes (the size threshold at which logs are dumped via dropbox)
     *  - long dumpThresholdTimeMs (the time threshold at which logs are dumped via dropbox)
     *  - long dumpWrappingTimeoutMs (the timeout at which wrapping will be interrupted, causing an immediate collection)
     */
    oneway void startContinuousLogging(in PersistableBundle options) = 2;

    /**
     * Stops continuous logging. If continuous logging is not running at the time of call,
     * this is a no-op.
     */
    oneway void stopContinuousLogging() = 3;

    /*
     * Q: if we add methods in the future,
     * how can the client check whether the service supports a newly added method?
     * Q: I tried using a FileDescriptor argument, but getting build error when
     * compiling Bort:
     * .../IDumpster.java:66: error: cannot find symbol
     *            _arg0 = data.readRawFileDescriptor();
     * readRawFileDescriptor is marked with @hidden, maybe it cannot be used when
     * building against the public SDK and only when building the app as part of aosp?
     */
}
