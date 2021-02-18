package com.memfault.bort

import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

interface MockSharedPreferences : SharedPreferences {
    val backingStorage: MutableMap<String, Any>
    val editor: SharedPreferences.Editor
}

fun makeFakeSharedPreferences(): MockSharedPreferences {
    val backingStorage = mutableMapOf<String, Any>()
    val editor = mockk<SharedPreferences.Editor> {
        every {
            putFloat(any(), any())
        } answers {
            backingStorage.put(firstArg(), secondArg())
            this@mockk
        }
        every {
            putString(any(), any())
        } answers {
            backingStorage.put(firstArg(), secondArg())
            this@mockk
        }
        every {
            apply()
        } just Runs
        every {
            remove(any())
        } answers {
            backingStorage.remove(firstArg())
            this@mockk
        }
    }

    val m: MockSharedPreferences = mockk {
        every {
            getFloat(any(), any())
        } answers {
            backingStorage[firstArg()] as Float? ?: secondArg()
        }
        every {
            getString(any(), any())
        } answers {
            backingStorage[firstArg()] as String? ?: secondArg()
        }
        every {
            edit()
        } returns editor
        every {
            all
        } returns backingStorage
    }
    every {
        m.backingStorage
    } returns backingStorage
    every {
        m.editor
    } returns editor
    return m
}
