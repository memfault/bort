package com.memfault.bort.shared

import android.os.Bundle
import com.memfault.bort.shared.PackageManagerCommand.Util.isValidAndroidApplicationId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class PackageManagerCommandTest {
    lateinit var bundleFactory: () -> Bundle
    lateinit var outBundle: Bundle

    @BeforeEach
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

    @ParameterizedTest
    @EnumSource
    fun flags(testCase: FlagsTestCase) {
        val flag = testCase.flag
        val cmd = testCase.cmd.copy(bundleFactory = bundleFactory)

        assertEquals(listOf("dumpsys", "package", flag), cmd.toList())
        cmd.toBundle()
        verify { outBundle.putBoolean(flag, true) }
        assertEquals(
            cmd,
            PackageManagerCommand.fromBundle(
                mockDeserializationBundle(flag = flag),
                bundleFactory = bundleFactory,
            ),
        )
    }

    @Test
    fun cmd() {
        val cmd = PackageManagerCommand(cmdOrAppId = "com.memfault.bort", bundleFactory = bundleFactory)
        assertEquals(listOf("dumpsys", "package", "com.memfault.bort"), cmd.toList())
        cmd.toBundle()
        verify { outBundle.putString("CMD", "com.memfault.bort") }
        assertEquals(
            cmd,
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
        assertEquals(
            listOf("dumpsys", "package", "invalid.package.id"),
            PackageManagerCommand.fromBundle(bundle).toList(),
        )
    }

    @Test
    fun isValidAndroidApplicationId() {
        assertEquals(false, "".isValidAndroidApplicationId())
        assertEquals(false, "myapp".isValidAndroidApplicationId())
        assertEquals(false, "com myapp".isValidAndroidApplicationId())
        assertEquals(true, "com.myapp".isValidAndroidApplicationId())
    }

    companion object {
    }
}
