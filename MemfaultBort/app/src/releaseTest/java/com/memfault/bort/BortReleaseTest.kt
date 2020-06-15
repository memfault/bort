package com.memfault.bort

open class BortReleaseTest : Bort() {
    override fun initComponents(): AppComponents.Builder =
        ReleaseTestComponentsBuilder(this)
}
