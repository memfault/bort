package com.memfault.bort.shared

import android.os.Bundle

interface Command {
    fun toList(): List<String>
    fun toBundle(): Bundle
}
