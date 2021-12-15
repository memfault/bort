package com.memfault.dumpster;

import com.memfault.dumpster.IDumpsterBasicCommandListener;

interface IDumpster {
    const int VERSION_INITIAL = 1;
    const int VERSION_BORT_ENABLED_PROPERTY = 2;
    const int VERSION_GETPROP_TYPES = 3;

    /**
     * Current version of the service.
     */
    const int VERSION = 3;

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
