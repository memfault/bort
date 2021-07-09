package com.memfault.bort.ota

import com.memfault.bort.ota.lib.Updater

interface AppComponents {
    fun updater(): Updater
}
