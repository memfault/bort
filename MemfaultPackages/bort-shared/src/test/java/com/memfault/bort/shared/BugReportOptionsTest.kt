package com.memfault.bort.shared

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.Test

class BugReportOptionsTest {
    @Test
    fun string() {
        assertThat(

            BugReportRequest.Component(
                "pkg",
                "cls",
            ).toString(),
        ).isEqualTo("pkg/cls")
    }

    @Test
    fun fromStringErrors() {
        assertFailure {
            BugReportRequest.Component.fromString("")
        }.isInstanceOf<IllegalArgumentException>()
        assertFailure {
            BugReportRequest.Component.fromString("com.package.foo")
        }.isInstanceOf<IllegalArgumentException>()
        assertFailure {
            BugReportRequest.Component.fromString("com.package.foo/")
        }.isInstanceOf<IllegalArgumentException>()
        assertFailure {
            BugReportRequest.Component.fromString("/my.class")
        }.isInstanceOf<IllegalArgumentException>()
    }
}
