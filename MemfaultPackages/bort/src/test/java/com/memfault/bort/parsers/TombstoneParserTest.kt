package com.memfault.bort.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TombstoneParserTest {
    @Test
    fun basic() {
        val tombstone = TombstoneParser(EXAMPLE_TOMBSTONE.byteInputStream()).parse()
        assertEquals(19474, tombstone.pid)
        assertEquals(19566, tombstone.tid)
        assertEquals("Chrome_IOThread", tombstone.threadName)
        assertEquals("com.android.chrome", tombstone.processName)
    }

    @Test
    fun failedToDump() {
        assertThrows<InvalidTombstoneException> {
            TombstoneParser("failed to dump process".byteInputStream()).parse()
        }
    }

    @Test
    fun missingThreadHeader() {
        assertThrows<InvalidTombstoneException>("Failed to find thread header") {
            TombstoneParser(
                "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***\n".byteInputStream(),
            ).parse()
        }
    }

    @Test
    fun invalidThreadHeader() {
        assertThrows<InvalidTombstoneException>("Thread header failed to parse") {
            TombstoneParser(
                """*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
                  |pid: xxx, tid: 19566, name: Chrome_IOThread  >>> com.android.chrome <<<
                """.trimMargin().byteInputStream(),
            ).parse()
        }
    }
}

val EXAMPLE_TOMBSTONE = """isPrevious: false
Build: google/taimen/taimen:9/PQ2A.190205.002/5164942:user/release-keys
Hardware: taimen
Revision: 0
Bootloader: unknown
Radio: unknown
Kernel: Linux version 3.18.74+ (android-build@wphs1.hot.corp.google.com) (gcc version 4.9.x-google 20140827 (prerelease) (GCC) ) #1 SMP PREEMPT Thu Oct 12 17:14:41 UTC 2017

*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
Build fingerprint: 'google/taimen/taimen:9/PQ2A.190205.002/5164942:user/release-keys'
Revision: 'rev_10'
ABI: 'arm'
pid: 19474, tid: 19566, name: Chrome_IOThread  >>> com.android.chrome <<<
signal 5 (SIGTRAP), code 1 (TRAP_BRKPT), fault addr 0xd37dbd50
Abort message: '[FATAL:gpu_data_manager_impl_private.cc(885)] GPU process isn't usable. Goodbye.
'
    r0  00000000  r1  e476c420  r2  00000400  r3  00000000
    r4  c84c422c  r5  c84c4230  r6  d35773a0  r7  e4c343c4
    r8  c84c3df8  r9  c84c3ddd  r10 c84c3ddc  r11 00000050
    ip  c84c3dfc  sp  c84c3dc8  lr  d1faa20f  pc  d1faa2e8

backtrace:
    #00 pc 0556e2e8  /data/app/com.android.chrome-JSttoNyJ9Ny0autYiZmEJg==/base.apk (offset 0x45cc000)

stack:
    c84c3d88  00000051

memory near r1 ([anon:libc_malloc]):
    e476c400 00000000 00000000 00000000 00000000  ................
    e476c410 00000000 00000000 00000000 00000000  ................

memory near pc (/data/app/com.android.chrome-JSttoNyJ9Ny0autYiZmEJg==/base.apk):
    d1faa2c8 fa38f000 0438f8dd 1000f8d9 bf011a08  ..8...8.........
    d1faa2d8 f50d4620 b0016d87 8ff0e8bd fe96f02d   F...m......-...

memory map (3 entries): (fault address prefixed with --->)
    d358f000-d3775fff r--         0    1e7000  /data/misc/shared_relro/libwebviewchromium32.relro
--->d3776000-d38c5fff rw-         0    150000  [anon:.bss]
    d38c6000-d768bfff ---         0   3dc6000
--- --- --- --- --- --- --- --- --- --- --- --- --- --- --- ---
pid: 19474, tid: 19474, name: .android.chrome  >>> com.android.chrome <<<
    r0  fffffffc  r1  fff0c220  r2  00000010  r3  ffffffff
    r4  00000000  r5  00000008  r6  e47a3420  r7  0000015a
    r8  e47a3420  r9  00000000  r10 e47a346c  r11 00000000
    ip  fff0c1e0  sp  fff0c1d0  lr  e4bb404d  pc  e4be1ac0

backtrace:
    #00 pc 00053ac0  /system/lib/libc.so (__epoll_pwait+20)
"""
