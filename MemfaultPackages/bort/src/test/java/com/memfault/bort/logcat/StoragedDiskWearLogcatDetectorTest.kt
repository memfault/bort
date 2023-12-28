package com.memfault.bort.logcat

import org.junit.Test

class StoragedDiskWearLogcatDetectorTest {

    @Test
    fun `test simple emmc string`() {
        val detector = StoragedDiskWearLogcatDetector()

        val metric = detector.detect("storaged_emmc_info: [ufs 310,1,1,1]")

        assert(metric?.version == "ufs 310") { metric.toString() }
        assert(metric?.eol == 1L) { metric.toString() }
        assert(metric?.lifetimeA == 1L) { metric.toString() }
        assert(metric?.lifetimeB == 1L) { metric.toString() }
    }

    @Test
    fun `test simple failures`() {
        val detector = StoragedDiskWearLogcatDetector()

        assert(detector.detect("storaged_emmc_info: [ufs 310,a,1,1]") == null)
        assert(detector.detect("storaged_emmc_info: [ufs 310,1,a,1]") == null)
        assert(detector.detect("storaged_emmc_info: [ufs 310,1,1,a]") == null)
        assert(detector.detect("storaged_emmc_info(20): [ufs 310,1,1,1]") == null)
        assert(detector.detect("storaged_emmc_info:") == null)
    }
}
