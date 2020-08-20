package com.memfault.dumpster;

import com.memfault.dumpster.IDumpsterBasicCommandListener;

interface IDumpster {
    /**
     * Current version of the service.
     */
    const int VERSION = 1;

    /**
    * Gets the version of the MemfaultDumpster service.
    */
    int getVersion() = 0;

    const int CMD_ID_GETPROP = 1;

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
