package com.memfault.bort.shared

import android.os.Bundle

private const val DELAY = "DELAY"

data class SleepCommand(val delaySeconds: Int) : Command {
    override fun toList(): List<String> = listOf("sleep", "$delaySeconds")

    override fun toBundle(): Bundle = Bundle().apply { putInt(DELAY, delaySeconds) }

    companion object {
        fun fromBundle(bundle: Bundle) = SleepCommand(bundle.getInt(DELAY))
    }
}
