package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NativeBacktraceParserParseProcessHeaderTest {
    @Test
    fun ok() {
        val (pid, time) = NativeBacktraceParser.parseProcessHeader("----- pid 9735 at 2019-08-21 12:17:22 -----")
        assertEquals(9735, pid)
        assertEquals("2019-08-21 12:17:22", time)
    }

    @Test
    fun badInput() {
        assertThrows<InvalidNativeBacktraceException> {
            NativeBacktraceParser.parseProcessHeader("")
        }
    }
}

class NativeBacktraceParserParseProcessMetadata {
    @Test
    fun ok() {
        val metadata = NativeBacktraceParser.parseProcessMetadata(
            """
                Cmd line: com.memfault.bort_e2e_helper
                ABI: 'x86'
                
                "bort_e2e_helper" sysTid=2535
            """.trimIndent().asLines()
        )
        assertEquals(
            mapOf(
                "Cmd line" to "com.memfault.bort_e2e_helper",
                "ABI" to "'x86'"
            ),
            metadata
        )
    }

    @Test
    fun badInput() {
        assertThrows<InvalidNativeBacktraceException> {
            NativeBacktraceParser.parseProcessMetadata(
                """
                Cmd line
                
                "bort_e2e_helper" sysTid=2535
                """.trimIndent().asLines()
            )
        }
    }
}

class NativeBacktraceParserTest {
    @Test
    fun ok() {
        val backtrace = NativeBacktraceParser(EXAMPLE_NATIVE_BACKTRACE.byteInputStream()).parse()
        assertEquals(
            backtrace,
            NativeBacktrace(
                processes = listOf(
                    NativeBacktrace.Process(1, "foo"),
                    NativeBacktrace.Process(2, "bar"),

                )
            )
        )
    }

    @Test
    fun empty() {
        assertThrows<InvalidNativeBacktraceException> {
            NativeBacktraceParser("".byteInputStream()).parse()
        }
    }
}

val EXAMPLE_NATIVE_BACKTRACE = """isPrevious: true
Build: generic/aosp_cf_x86_phone/vsoc_x86:9/PPRL.190801.002/root04302340:userdebug/test-keys
Hardware: cutf
Revision: 0
Bootloader: unknown
Radio: 
Kernel: Linux version 4.14.175-g263c7aebe991 (android-build@abfarm201)



----- pid 1 at 2020-11-20 16:05:54 -----
Cmd line: foo
ABI: 'x86'

"bort_e2e_helper" sysTid=2535
  #00 pc 00000ef9  [vdso:eaec5000] (__kernel_vsyscall+9)
  #01 pc 0007617c  /system/lib/libc.so (___rt_sigqueueinfo+28)
  #02 pc 0002f918  /system/lib/libc.so (sigqueue+344)

"Jit thread pool" sysTid=2540
  #00 pc 00000ef9  [vdso:eaec5000] (__kernel_vsyscall+9)
  #01 pc 0001fdf8  /system/lib/libc.so (syscall+40)
  #02 pc 000abc5e  /system/lib/libart.so (art::ConditionVariable::WaitHoldingLocks(art::Thread*)+110)

----- end 1 -----

----- pid 2 at 2020-11-20 16:05:54 -----
Cmd line: bar
ABI: 'x86'

"my_thread" sysTid=2535
  #00 pc 00000ef9  [vdso:eaec5000] (__kernel_vsyscall+9)
  #01 pc 0007617c  /system/lib/libc.so (___rt_sigqueueinfo+28)
  #02 pc 0002f918  /system/lib/libc.so (sigqueue+344)

----- end 2 -----
"""
