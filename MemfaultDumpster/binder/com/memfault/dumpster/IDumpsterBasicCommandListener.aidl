package com.memfault.dumpster;

interface IDumpsterBasicCommandListener {
    /**
     * Called when the command finished.
     */
    oneway void onFinished(int statusCode, @nullable String output) = 1;

    /**
     * Called when the command is not supported by the service.
     */
    oneway void onUnsupported() = 2;
}
