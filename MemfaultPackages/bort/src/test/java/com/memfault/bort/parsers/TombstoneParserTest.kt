package com.memfault.bort.parsers

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Test

class TombstoneParserTest {
    @Test
    fun basic() {
        val tombstone = TombstoneParser(EXAMPLE_TOMBSTONE.byteInputStream()).parse()
        assertThat(tombstone.pid).isEqualTo(19474)
        assertThat(tombstone.tid).isEqualTo(19566)
        assertThat(tombstone.threadName).isEqualTo("Chrome_IOThread")
        assertThat(tombstone.processName).isEqualTo("com.android.chrome")
    }

    @Test
    fun failedToDump() {
        assertFailure {
            TombstoneParser("failed to dump process".byteInputStream()).parse()
        }.isInstanceOf<InvalidTombstoneException>()
    }

    @Test
    fun missingThreadHeader() {
        assertFailure {
            TombstoneParser(
                "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***\n".byteInputStream(),
            ).parse()
        }.isInstanceOf<InvalidTombstoneException>()
            .hasMessage("Failed to find thread header")
    }

    @Test
    fun invalidThreadHeader() {
        assertFailure {
            TombstoneParser(
                """*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
                  |pid: xxx, tid: 19566, name: Chrome_IOThread  >>> com.android.chrome <<<
                """.trimMargin().byteInputStream(),
            ).parse()
        }.isInstanceOf<InvalidTombstoneException>()
            .hasMessage("Failed to find thread header")
    }

    @Test
    fun spaceInThreadName_processInProcessName() {
        val tombstone = TombstoneParser(EXAMPLE_TOMBSTONE_THREADNAME_PROCESSNAME.byteInputStream()).parse()
        assertThat(tombstone.pid).isEqualTo(1700)
        assertThat(tombstone.tid).isEqualTo(1923)
        assertThat(tombstone.threadName).isEqualTo("AsyncTask #1")
        assertThat(tombstone.processName).isEqualTo("com.android.nfc")
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

val EXAMPLE_TOMBSTONE_THREADNAME_PROCESSNAME = """isPrevious: true
Build: Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys
Hardware: aosp_arm64
Revision: 0
Bootloader: unknown
Radio: null
Kernel: Linux version 4.14.156-perf+ (build@build-dev-8) (clang version 8.0.12 for Android NDK) #1 SMP PREEMPT Wed Jul 6 03:48:53 UTC 2022

*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
Build fingerprint: 'Android/aosp_arm64/generic_arm64:8.1.0/OC/root04302340:eng/test-keys'
Revision: '0'
ABI: 'arm64'
Timestamp: 2023-09-23 09:12:30-0600
pid: 1700, tid: 1923, name: AsyncTask #1  >>> com.android.nfc:phoenix <<<
uid: 1027
signal 6 (SIGABRT), code -1 (SI_QUEUE), fault addr --------
Abort message: 'FORTIFY: pthread_mutex_lock called on a destroyed mutex (0x74cf80ac18)'
    x0  0000000000000000  x1  0000000000000783  x2  0000000000000006  x3  00000074793e98a0
    x4  0000000000000000  x5  0000000000000000  x6  0000000000000000  x7  0000000000000038
    x8  00000000000000f0  x9  f12ec1ea89763424  x10 0000000000000001  x11 0000000000000000
    x12 fffffff0fffffbdf  x13 0000000000000004  x14 00000074793e940f  x15 0000000000000005
    x16 0000007561dd58c0  x17 0000007561db1890  x18 00000074781ba000  x19 00000000000006a4
    x20 0000000000000783  x21 00000000ffffffff  x22 00000074cf9ff584  x23 00000074cf9ff598
    x24 00000074cf80ac60  x25 00000074793e9d50  x26 00000074793ea020  x27 000000747b5fc020
    x28 00000074cf80acd8  x29 00000074793e9940
    sp  00000074793e9880  lr  0000007561d63048  pc  0000007561d63074
"""
