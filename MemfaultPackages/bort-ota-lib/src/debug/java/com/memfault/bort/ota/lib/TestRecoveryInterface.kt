package com.memfault.bort.ota.lib

import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class, replaces = [RealRecoveryInterface::class])
class TestRecoveryInterface @Inject constructor() : RecoveryInterface {
    override fun verifyOrThrow(path: File) {
        Logger.test("verify path=$path")
    }

    override fun install(path: File) {
        Logger.test("install path=$path")
    }
}
