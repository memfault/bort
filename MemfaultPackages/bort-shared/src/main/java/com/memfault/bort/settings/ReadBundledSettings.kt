package com.memfault.bort.settings

import android.content.res.Resources
import com.memfault.bort.shared.DEFAULT_SETTINGS_ASSET_FILENAME

fun Resources.readBundledSettings() = assets
    .open(DEFAULT_SETTINGS_ASSET_FILENAME)
    .use {
        it.bufferedReader().readText()
    }
