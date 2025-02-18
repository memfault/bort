package com.memfault.bort

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.memfault.bort.settings.ConfigValue
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class PackageNameAllowListTest {
    private val FIXTURE = listOf(
        AndroidAppIdScrubbingRule("com.*"),
        AndroidAppIdScrubbingRule("net.something.*"),
        AndroidAppIdScrubbingRule("org.*.midmatcher"),
    )

    @Test
    fun filtersGlobs() {
        val allowList = RuleBasedPackageNameAllowList { FIXTURE }

        assertThat(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
                "comd.should.not.match",
                "org.anything.goes.midmatcher",
            ).filter { it in allowList },
        ).isEqualTo(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "org.anything.goes.midmatcher",
            ),
        )
    }

    @Test
    fun doesNotFilterSystemProcesses() {
        val allowList = RuleBasedPackageNameAllowList { FIXTURE }

        assertThat(
            listOf(
                "/one",
                "SystemServer",
                "netd",
            ).filter { it in allowList },
        ).isEqualTo(
            listOf(
                "/one",
                "SystemServer",
                "netd",
            ),
        )
    }

    @Test
    fun doesNotFilterMemfaultPackages() {
        val allowList = RuleBasedPackageNameAllowList { FIXTURE }

        assertThat(
            listOf(
                "/one",
                "SystemServer",
                "netd",
                "com.memfault.usagereporter",
            ).filter { it in allowList },
        ).isEqualTo(
            listOf(
                "/one",
                "SystemServer",
                "netd",
                "com.memfault.usagereporter",
            ),
        )
    }

    @Test
    fun noFiltersMatchEverything() {
        val allowList = RuleBasedPackageNameAllowList { listOf() }
        assertThat(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
            ).filter { it in allowList },
        ).isEqualTo(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
            ),
        )
    }

    @Test
    fun testRegexCacheInvalidation() {
        val rules: ConfigValue<List<AndroidAppIdScrubbingRule>> = mockk()
        val allowList = RuleBasedPackageNameAllowList(rules)

        every { rules() } returns FIXTURE
        assertThat(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
            ).filter { it in allowList },
        ).isEqualTo(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
            ),
        )

        every { rules() } returns listOf()
        assertThat(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
            ).filter { it in allowList },
        ).isEqualTo(
            listOf(
                "com.memfault.smartchair.bort",
                "net.something.test",
                "net.anotherthing.test",
                "org.willnotmatch",
            ),
        )
    }
}
