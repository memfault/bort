package com.memfault.bort.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BugReportOptionsTest {
    @Test
    fun string() {
        assertEquals(
            "pkg/cls",
            BugReportRequest.Component(
                "pkg",
                "cls",
            ).toString(),
        )
    }

    @Test
    fun fromStringErrors() {
        assertThrows<IllegalArgumentException> {
            BugReportRequest.Component.fromString("")
        }
        assertThrows<IllegalArgumentException> {
            BugReportRequest.Component.fromString("com.package.foo")
        }
        assertThrows<IllegalArgumentException> {
            BugReportRequest.Component.fromString("com.package.foo/")
        }
        assertThrows<IllegalArgumentException> {
            BugReportRequest.Component.fromString("/my.class")
        }
    }
}
