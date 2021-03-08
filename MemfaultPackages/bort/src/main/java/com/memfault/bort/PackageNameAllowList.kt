package com.memfault.bort

import com.memfault.bort.settings.ConfigValue
import com.memfault.bort.shared.PackageManagerCommand.Util.isValidAndroidApplicationId
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

fun interface PackageNameAllowList {
    operator fun contains(packageName: String?): Boolean
}

class RuleBasedPackageNameAllowList(
    private val rulesConfig: ConfigValue<List<AndroidAppIdScrubbingRule>>
) : PackageNameAllowList {
    // These are semi-expensive to compute, so we cache them until rules change
    private var cachedRegexes: List<Regex> = emptyList()
    private var cachedRules: List<AndroidAppIdScrubbingRule> = emptyList()
    private val cacheLock: ReentrantLock = ReentrantLock()

    override operator fun contains(packageName: String?): Boolean {
        return packageName == null || !packageName.isValidAndroidApplicationId() ||
            matchesRules(regexesFor(rulesConfig()), packageName)
    }

    private fun matchesRules(regexes: List<Regex>, packageName: String): Boolean =
        regexes.isEmpty() || regexes.any { it.containsMatchIn(packageName) }

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

/**
 * Converts a string containing a star-glob to a Regex. All string elements are escaped in \Q\E, except for
 * asterisks which get converted to a Regex match-any (.*).
 */
private fun String.toGlobRegex(): Regex =
    this.split("*")
        .joinToString(
            prefix = "\\Q",
            separator = "\\E.*\\Q",
            postfix = "\\E",
        ).toRegex()
