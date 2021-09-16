package com.memfault.bort

import com.memfault.bort.settings.ConfigValue
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageNameAllowListTest {
    private val FIXTURE = listOf(
        AndroidAppIdScrubbingRule("com.*"),
        AndroidAppIdScrubbingRule("net.something.*"),
        AndroidAppIdScrubbingRule("org.*.midmatcher")
    )

    @Test
    fun filtersGlobs() {
        val allowList = RuleBasedPackageNameAllowList { FIXTURE }

        assertEquals(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "org.anything.goes.midmatcher",
            ),
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
                "comd.should.not.match",
                "org.anything.goes.midmatcher",
            ).filter { it in allowList }
        )
    }

    @Test
    fun doesNotFilterSystemProcesses() {
        val allowList = RuleBasedPackageNameAllowList { FIXTURE }

        assertEquals(
            listOf(
                "/one",
                "SystemServer",
                "netd",
            ),
            listOf(
                "/one",
                "SystemServer",
                "netd",
            ).filter { it in allowList }
        )
    }

    @Test
    fun doesNotFilterMemfaultPackages() {
        val allowList = RuleBasedPackageNameAllowList { FIXTURE }

        assertEquals(
            listOf(
                "/one",
                "SystemServer",
                "netd",
                "com.memfault.usagereporter",
            ),
            listOf(
                "/one",
                "SystemServer",
                "netd",
                "com.memfault.usagereporter",
            ).filter { it in allowList }
        )
    }

    @Test
    fun noFiltersMatchEverything() {
        val allowList = RuleBasedPackageNameAllowList { listOf() }
        assertEquals(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch"
            ),
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch"
            ).filter { it in allowList }
        )
    }

    @Test
    fun testRegexCacheInvalidation() {
        val rules: ConfigValue<List<AndroidAppIdScrubbingRule>> = mockk()
        val allowList = RuleBasedPackageNameAllowList(rules)

        every { rules() } returns FIXTURE
        assertEquals(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
            ),
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch"
            ).filter { it in allowList }
        )

        every { rules() } returns listOf()
        assertEquals(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch"
            ),
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch"
            ).filter { it in allowList }
        )
    }
}
