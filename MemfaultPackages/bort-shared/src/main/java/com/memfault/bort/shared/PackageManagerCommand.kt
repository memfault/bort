package com.memfault.bort.shared

import android.os.Bundle

/**
 * Data class to hold most of the possible flags and options to the `dumpsys package` command line
 * interface.
 *
 * Package manager dump options:
 *  [-h] [-f] [--checkin] [--all-components] [cmdOrAppId] ...
 *    --checkin: dump for a checkin
 *    -f: print details of intent filters
 *    -h: print this help
 *    --all-components: include all component names in package dump
 *  cmd may be one of:
 *    apex: list active APEXes and APEX session state
 *    l[ibraries]: list known shared libraries
 *    f[eatures]: list device features
 *    k[eysets]: print known keysets
 *    r[esolvers] [activity|service|receiver|content]: dump intent resolvers
 *    perm[issions]: dump permissions
 *    permission [name ...]: dump declaration and use of given permission
 *    pref[erred]: print preferred package settings
 *    preferred-xml [--full]: print preferred package settings as xml
 *    prov[iders]: dump content providers
 *    p[ackages]: dump installed packages
 *    q[ueries]: dump app queryability calculations
 *    s[hared-users]: dump shared user IDs
 *    m[essages]: print collected runtime messages
 *    v[erifiers]: print package verifier info
 *    d[omain-preferred-apps]: print domains preferred apps
 *    i[ntent-filter-verifiers]|ifv: print intent filter verifier info
 *    version: print database version info
 *    write: write current settings now
 *    installs: details about install sessions
 *    check-permission <permission> <package> [<user>]: does pkg hold perm?
 *    dexopt: dump dexopt state
 *    compiler-stats: dump compiler statistics
 *    service-permissions: dump permissions required by services
 *    <package.name>: info about given package
 */
data class PackageManagerCommand(
    val checkin: Boolean = false,
    val filters: Boolean = false,
    val allComponents: Boolean = false,
    val help: Boolean = false,
    val cmdOrAppId: String? = null,
    val bundleFactory: () -> Bundle = ::Bundle,
) : Command {
    private val booleanFlagMap
        get() = mapOf(
            CHECKIN to checkin,
            FILTERS to filters,
            ALL_COMPONENTS to allComponents,
            HELP to help,
        )

    override fun toList(): List<String> =
        listOf("dumpsys", "package") +
            booleanFlagMap
                .filter { (_, value) -> value }
                .map { (flag, _) -> flag } + cmdOrAppId.listify()

    override fun toBundle(): Bundle =
        bundleFactory().apply {
            for ((key, value) in booleanFlagMap) {
                if (value) putBoolean(key, value)
            }
            cmdOrAppId?.let { putString(CMD, cmdOrAppId) }
        }

    companion object Util {
        const val CMD_APEX = "apex"
        const val CMD_LIBRARIES = "l"
        const val CMD_FEATURES = "f"
        const val CMD_KEYSETS = "k"
        const val CMD_RESOLVERS = "r"
        const val CMD_PERMISSIONS = "perm"
        const val CMD_PREFERRED = "pref"
        const val CMD_PREFERRED_XML = "preferred-xml"
        const val CMD_PROVIDERS = "prov"
        const val CMD_PACKAGES = "p"
        const val CMD_QUERIES = "q"
        const val CMD_SHARED_USERS = "s"
        const val CMD_MESSAGES = "m"
        const val CMD_VERIFIERS = "v"
        const val CMD_DOMAIN_PREFERRED_APPS = "d"
        const val CMD_INTENT_FILTER_VERIFIERS = "i"
        const val CMD_VERSION = "version"
        const val CMD_INSTALLS = "installs"
        const val CMD_DEXOPT = "dexopt"
        const val CMD_COMPILER_STATS = "compiler-stats"
        const val CMD_SERVICE_PERMISSIONS = "service-permissions"

        private fun String.sanitizeCmd(): String {
            if (isPermittedCmd()) return this
            return INVALID_PACKAGE_ID
        }

        fun String.isPermittedCmd(): Boolean =
            isBuiltinCmd() or isValidAndroidApplicationId()

        fun String.isBuiltinCmd(): Boolean =
            setOf(
                CMD_APEX,
                CMD_LIBRARIES,
                CMD_FEATURES,
                CMD_KEYSETS,
                CMD_RESOLVERS,
                CMD_PERMISSIONS,
                CMD_PREFERRED,
                CMD_PREFERRED_XML,
                CMD_PROVIDERS,
                CMD_PACKAGES,
                CMD_QUERIES,
                CMD_SHARED_USERS,
                CMD_MESSAGES,
                CMD_VERIFIERS,
                CMD_DOMAIN_PREFERRED_APPS,
                CMD_INTENT_FILTER_VERIFIERS,
                CMD_VERSION,
                CMD_INSTALLS,
                CMD_DEXOPT,
                CMD_COMPILER_STATS,
                CMD_SERVICE_PERMISSIONS,
            ).contains(this)

        fun String.isValidAndroidApplicationId(): Boolean {
            // See https://developer.android.com/studio/build/application-id
            if (length > MAX_APPLICATION_ID_LENGTH) return false
            if (!this.matches(Regex("""[a-zA-Z0-9_.]+"""))) return false
            if (!this.contains('.')) return false
            return true
        }

        fun fromBundle(bundle: Bundle, bundleFactory: () -> Bundle = ::Bundle) = with(bundle) {
            PackageManagerCommand(
                checkin = getBoolean(CHECKIN),
                filters = getBoolean(FILTERS),
                help = getBoolean(HELP),
                allComponents = getBoolean(ALL_COMPONENTS),
                cmdOrAppId = getString(CMD)?.sanitizeCmd(),
                bundleFactory = bundleFactory,
            )
        }
    }
}

private const val CHECKIN = "--checkin"
private const val FILTERS = "-f"
private const val HELP = "-h"
private const val ALL_COMPONENTS = "--all-components"
private const val CMD = "CMD"

private const val INVALID_PACKAGE_ID = "invalid.package.id"

// This is arbitrary, but do want to enforce a maximum length for safety
private const val MAX_APPLICATION_ID_LENGTH = 512
