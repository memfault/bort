package com.memfault.bort.logcat

import androidx.annotation.VisibleForTesting
import com.memfault.bort.clientserver.MarMetadata
import com.memfault.bort.dropbox.allowedByRateLimit
import com.memfault.bort.metrics.CrashHandler
import com.memfault.bort.parsers.LogcatLine
import com.memfault.bort.parsers.PackageManagerReport
import com.memfault.bort.settings.SettingsProvider
import com.memfault.bort.shared.Logger
import com.memfault.bort.time.AbsoluteTime
import com.memfault.bort.time.CombinedTimeProvider
import com.memfault.bort.tokenbucket.SelinuxViolations
import com.memfault.bort.tokenbucket.TokenBucketStore
import com.memfault.bort.uploader.EnqueueUpload
import com.memfault.bort.uploader.HandleEventOfInterest
import okio.Buffer
import java.time.Instant
import javax.inject.Inject

data class SelinuxViolation(
    val rawDenial: String,
    val uid: Int?,
    val timestamp: Instant?,
    val action: String?,
    val sourceContext: String?,
    val targetContext: String?,
    val targetClass: String?,
    val app: String?,
    val comm: String?,
    val name: String?,
    val packageName: String?,
    val packageVersionName: String?,
    val packageVersionCode: Long?,
)

private fun Buffer.writeStringUtfNotNull(s: String?): Buffer = if (s != null) writeUtf8(s) else this

class SelinuxViolationLogcatDetector
@Inject constructor(
    private val combinedTimeProvider: CombinedTimeProvider,
    private val enqueueUpload: EnqueueUpload,
    private val handleEventOfInterest: HandleEventOfInterest,
    private val settingsProvider: SettingsProvider,
    @SelinuxViolations private val tokenBucketStore: TokenBucketStore,
    private val crashHandler: CrashHandler,
) {
    fun process(
        line: LogcatLine,
        packageManagerReport: PackageManagerReport,
    ) {
        // Avoid processing if the data source is disabled. Potentially could perform this check inside the
        // [parse] or [record] functions too, but doing it here avoids having to mock out the method in tests.
        if (!settingsProvider.selinuxViolationSettings.dataSourceEnabled) return

        val violationOrNull = parse(line, packageManagerReport)
        violationOrNull?.let { record(it) }
    }

    fun parse(
        line: LogcatLine,
        packageManagerReport: PackageManagerReport,
    ): SelinuxViolation? = parse(
        message = line.message,
        uid = line.uid,
        timestamp = line.logTime,
        packageManagerReport = packageManagerReport,
    )

    // Separate method to make it easier to test without constructing a [LogcatLine].
    @VisibleForTesting
    fun parse(
        message: String?,
        uid: Int?,
        timestamp: Instant?,
        packageManagerReport: PackageManagerReport,
    ): SelinuxViolation? {
        // Bail early if line doesn't start with "type=". Technically we're just checking for 't' because the regex
        // should operate quickly too.
        if (message?.getOrNull(0) != 't') return null

        if (AVC_CHECK_PATTERN.find(message) == null) return null

        val appOrNull = AVC_APP_PATTERN.find(message)?.groupValues?.getOrNull(1)

        val matchingPackageOrNull = appOrNull
            ?.let { app -> packageManagerReport.packages.singleOrNull { it.id == app } }
            ?: packageManagerReport.packages.singleOrNull { it.userId == uid }

        return SelinuxViolation(
            rawDenial = message,
            uid = uid,
            timestamp = timestamp,
            action = AVC_ACTION_PATTERN.find(message)?.groupValues?.getOrNull(1),
            sourceContext = AVC_SOURCE_CONTEXT_PATTERN.find(message)?.groupValues?.getOrNull(1),
            targetContext = AVC_TARGET_CONTEXT_PATTERN.find(message)?.groupValues?.getOrNull(1),
            targetClass = AVC_TARGET_CLASS_PATTERN.find(message)?.groupValues?.getOrNull(1),
            app = appOrNull,
            comm = AVC_COMM_PATTERN.find(message)?.groupValues?.getOrNull(1),
            name = AVC_NAME_PATTERN.find(message)?.groupValues?.getOrNull(1),
            packageName = matchingPackageOrNull?.id,
            packageVersionName = matchingPackageOrNull?.versionName,
            packageVersionCode = matchingPackageOrNull?.versionCode,
        )
    }

    private fun record(selinuxViolation: SelinuxViolation) {
        // Create a local-only key to dedupe SELinux violation uploads.
        val dedupeKey = Buffer()
            .writeStringUtfNotNull(selinuxViolation.action)
            .writeStringUtfNotNull(scrubUntrustedAppIndex(selinuxViolation.sourceContext))
            .writeStringUtfNotNull(selinuxViolation.app)
            .writeStringUtfNotNull(selinuxViolation.comm)
            .writeStringUtfNotNull(selinuxViolation.name)
            .sha256()
            .hex()

        val allowedByRateLimit = tokenBucketStore.takeSimple(
            tag = "selinux-violation",
            key = dedupeKey,
        )

        if (!allowedByRateLimit) return

        val metadata = MarMetadata.SelinuxViolationMarMetadata(
            rawDenial = selinuxViolation.rawDenial,
            packageName = selinuxViolation.packageName,
            packageVersionName = selinuxViolation.packageVersionName,
            packageVersionCode = selinuxViolation.packageVersionCode,
        )

        enqueueUpload.enqueue(
            file = null,
            metadata = metadata,
            collectionTime = combinedTimeProvider.now(),
        )

        selinuxViolation.timestamp?.let {
            handleEventOfInterest.handleEventOfInterest(AbsoluteTime(it))
        }
        crashHandler.onCrash()

        val actor = scrubObjectString(selinuxViolation.app ?: selinuxViolation.sourceContext).orEmpty()
        val actorHint = scrubObjectString(selinuxViolation.name ?: selinuxViolation.comm)

        val actorTitle = actorHint?.let { "$actor ($it)" } ?: actor

        Logger.test(
            "Recorded SELinux violation: " +
                "Denied ${selinuxViolation.action} for $actorTitle",
        )
    }

    /**
     * Scrub an SELinux label (u:object_r:storaged_data_file:s0) into just the type (storaged_data_file).
     */
    private fun scrubObjectString(s: String?): String? =
        s?.let {
            OBJECT_PATTERN.find(it)?.groupValues?.getOrNull(1) ?: s
        }

    /**
     * Scrub the untrusted app index so that it can be used in a signature
     *
     * It must be scrubbed because it is not stable across devices
     */
    private fun scrubUntrustedAppIndex(s: String?): String? =
        if (s?.startsWith(UNTRUSTED_APP_PREFIX) == true) UNTRUSTED_APP_PREFIX else s

    companion object {
        private val AVC_CHECK_PATTERN = Regex("""type=(1400|AVC)\s+(msg=)?audit\([\d\.:]+\):\s+avc:\s+denied\s+""")
        private val OBJECT_PATTERN = Regex("""u:\S+:(\S+):s0""")

        private val AVC_ACTION_PATTERN = Regex("""\{\s([^}]+)\s\}""")
        private val AVC_SOURCE_CONTEXT_PATTERN = Regex("""scontext=u:\S+:(\S+):s0""")
        private val AVC_TARGET_CONTEXT_PATTERN = Regex("""tcontext=u:\S+:(\S+):s0""")
        private val AVC_TARGET_CLASS_PATTERN = Regex("""tclass=(\w+)""")
        private val AVC_APP_PATTERN = Regex("""app=(\S+)""")
        private val AVC_COMM_PATTERN = Regex("""comm="(\S+)"""")
        private val AVC_NAME_PATTERN = Regex("""name="(\S+)"""")

        private const val UNTRUSTED_APP_PREFIX = "untrusted_app_"
    }
}
