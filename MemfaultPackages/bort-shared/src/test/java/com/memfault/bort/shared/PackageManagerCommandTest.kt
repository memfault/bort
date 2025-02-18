package com.memfault.bort.shared

import android.os.Bundle
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.google.testing.junit.testparameterinjector.TestParameter
import com.google.testing.junit.testparameterinjector.TestParameterInjector
import com.memfault.bort.shared.PackageManagerCommand.Util.isValidAndroidApplicationId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(TestParameterInjector::class)
class PackageManagerCommandTest {
    lateinit var bundleFactory: () -> Bundle
    lateinit var outBundle: Bundle

    @Before
    fun setUp() {
        outBundle = mockk(relaxed = true)
        bundleFactory = mockk(relaxed = true)
        every { bundleFactory() } returns outBundle
    }

    private fun mockDeserializationBundle(cmd: String? = null, flag: String? = null) =
        mockk<Bundle> {
            every { getBoolean(any()) } returns false
            flag?.let {
                every { getBoolean(flag) } returns true
            }
            every { getString("CMD") } returns cmd
        }

    enum class FlagsTestCase(
        val flag: String,
        val cmd: PackageManagerCommand,
    ) {
        CHECKIN("--checkin", PackageManagerCommand(checkin = true)),
        FILTERS("-f", PackageManagerCommand(filters = true)),
        ALL_COMPONENTS("--all-components", PackageManagerCommand(allComponents = true)),
        HELP("-h", PackageManagerCommand(help = true)),
    }

    @Test
    fun flags(@TestParameter testCase: FlagsTestCase) {
        val flag = testCase.flag
        val cmd = testCase.cmd.copy(bundleFactory = bundleFactory)

        assertThat(cmd.toList()).containsExactly("dumpsys", "package", flag)
        cmd.toBundle()
        verify { outBundle.putBoolean(flag, true) }
        assertThat(cmd).isEqualTo(
            PackageManagerCommand.fromBundle(
                mockDeserializationBundle(flag = flag),
                bundleFactory = bundleFactory,
            ),
        )
    }

    @Test
    fun cmd() {
        val cmd = PackageManagerCommand(cmdOrAppId = "com.memfault.bort", bundleFactory = bundleFactory)
        assertThat(cmd.toList()).containsExactly("dumpsys", "package", "com.memfault.bort")
        cmd.toBundle()
        verify { outBundle.putString("CMD", "com.memfault.bort") }
        assertThat(cmd).isEqualTo(
            PackageManagerCommand.fromBundle(
                mockDeserializationBundle(cmd = "com.memfault.bort"),
                bundleFactory = bundleFactory,
            ),
        )
    }

    @Test
    fun sanitizeCmdWhenDeserializing() {
        val bundle: Bundle = mockk {
            every { getBoolean(any()) } returns false
            every { getString("CMD") } returns "&& bad command"
        }
        assertThat(PackageManagerCommand.fromBundle(bundle).toList())
            .containsExactly("dumpsys", "package", "invalid.package.id")
    }

    @Test
    fun isValidAndroidApplicationId() {
        assertThat("".isValidAndroidApplicationId()).isFalse()
        assertThat("myapp".isValidAndroidApplicationId()).isFalse()
        assertThat("com myapp".isValidAndroidApplicationId()).isFalse()
        assertThat("com.myapp".isValidAndroidApplicationId()).isTrue()
    }
}
