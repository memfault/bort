package com.memfault.bort

import com.memfault.bort.regex.toGlobRegex
import com.memfault.bort.settings.RulesConfig
import com.memfault.bort.shared.APPLICATION_ID_MEMFAULT_USAGE_REPORTER
import com.memfault.bort.shared.PackageManagerCommand.Util.isValidAndroidApplicationId
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

fun interface PackageNameAllowList {
    operator fun contains(packageName: String?): Boolean
}

@Singleton
@ContributesBinding(SingletonComponent::class)
class RuleBasedPackageNameAllowList @Inject constructor(
    private val rulesConfig: RulesConfig,
) : PackageNameAllowList {
    // These are semi-expensive to compute, so we cache them until rules change
    private var cachedRegexes: List<Regex> = emptyList()
    private var cachedRules: List<AndroidAppIdScrubbingRule> = emptyList()
    private val cacheLock: ReentrantLock = ReentrantLock()

    // Always allow memfault packages (but not as part of main rules config - that would force package scrubbing to
    // be enabled below).
    private val internalPackages = listOf(BuildConfig.APPLICATION_ID, APPLICATION_ID_MEMFAULT_USAGE_REPORTER)

    override operator fun contains(packageName: String?): Boolean = packageName == null ||
        !packageName.isValidAndroidApplicationId() ||
        matchesRules(regexesFor(rulesConfig()), packageName)

    private fun matchesRules(regexes: List<Regex>, packageName: String): Boolean =
        regexes.isEmpty() ||
            regexes.any { it.containsMatchIn(packageName) } ||
            internalPackages.any { it == packageName }

    private fun regexesFor(rulesConfig: List<AndroidAppIdScrubbingRule>): List<Regex> = cacheLock.withLock {
        when (cachedRules) {
            rulesConfig -> cachedRegexes
            else -> rulesConfig.map { it.appIdPattern.toGlobRegex() }.also {
                cachedRules = rulesConfig
                cachedRegexes = it
            }
        }
    }
}
