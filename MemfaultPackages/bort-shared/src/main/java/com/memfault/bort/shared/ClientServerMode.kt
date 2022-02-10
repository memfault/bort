package com.memfault.bort.shared

/**
 * Is Bort operating in client/server mode, where a client Bort app is piping data to a server Bort app on another
 * device?
 *
 * This is configured using the system property vendor.memfault.bort.client.server.mode
 *
 * If so, then this device will be either [SERVER] (connected to the internet) or [CLIENT] (no connection to internet).
 *
 * If not, then it is [DISABLED], and none of the related functionality will be enabled.
 */
enum class ClientServerMode {
    DISABLED,
    CLIENT,
    SERVER,
    ;

    companion object {
        const val SYSTEM_PROP = "vendor.memfault.bort.client.server.mode"

        fun decode(value: String?) =
            when (value) {
                "server" -> SERVER
                "client" -> CLIENT
                else -> DISABLED
            }
    }
}
