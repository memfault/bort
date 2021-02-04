package com.memfault.bort

import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

fun makeFakeSharedPreferences(): SharedPreferences {
    val backingStorage = mutableMapOf<String, Float>()
    val editor = mockk<SharedPreferences.Editor> {
        every {
            putFloat(any(), any())
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

    return mockk {
        every {
            getFloat(any(), any())
        } answers {
            backingStorage[firstArg()] ?: secondArg()
        }
        every {
            edit()
        } returns editor
        every {
            all
        } returns backingStorage
    }
}
