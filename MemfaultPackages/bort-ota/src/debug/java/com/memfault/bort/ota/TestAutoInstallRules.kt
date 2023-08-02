package com.memfault.bort.ota

import androidx.work.NetworkType
import com.memfault.bort.ota.lib.DownloadOtaRules
import com.memfault.bort.ota.lib.Ota
import com.memfault.bort.ota.lib.OtaRulesProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

@ContributesBinding(SingletonComponent::class, replaces = [AutoInstallRules::class])
class TestAutoInstallRules @Inject constructor(
    private val mainRules: AutoInstallRules,
) : OtaRulesProvider by mainRules {
    // Emulator doesn't have network, according to JobScheduler.
    override fun downloadRules(ota: Ota): DownloadOtaRules {
        return mainRules.downloadRules(ota).copy(
            overrideNetworkConstraint = NetworkType.NOT_REQUIRED,
            useForegroundServiceForAbDownloads = true,
        )
    }
}
