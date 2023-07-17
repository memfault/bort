package com.memfault.bort.ota.lib

import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class, replaces = [RealRecoveryInterface::class])
class TestRecoveryInterface @Inject constructor() : RecoveryInterface {
    override fun verifyOrThrow(path: File) {
        testLog("verify path=$path")
    }

    override fun install(path: File) {
        testLog("install path=$path")
    }
}
