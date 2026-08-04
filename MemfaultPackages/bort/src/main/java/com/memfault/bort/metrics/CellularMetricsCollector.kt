package com.memfault.bort.metrics

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.TelephonyManager
import com.memfault.bort.AndroidSdkVersion
import com.memfault.bort.reporting.NumericAgg.LATEST_VALUE
import com.memfault.bort.reporting.Reporting
import com.memfault.bort.settings.MetricsSettings
import com.memfault.bort.shared.Logger
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val NETWORK_TYPE_LTE_CA = 19

fun interface CellularSignalStrengthProvider {
    fun get(): android.telephony.SignalStrength?
}

@ContributesBinding(SingletonComponent::class)
class RealCellularSignalStrengthProvider @Inject constructor(
    private val application: Application,
    @AndroidSdkVersion private val androidSdkVersion: Int,
) : CellularSignalStrengthProvider {
    /**
     * TelephonyManager.getSignalStrength() is only present on the platform from API 28 onwards:
     * calling it below that throws NoSuchMethodError.
     */
    @SuppressLint("NewApi")
    override fun get(): android.telephony.SignalStrength? {
        if (androidSdkVersion < Build.VERSION_CODES.P) return null
        val tm = application.getSystemService(TelephonyManager::class.java) ?: return null
        return tm.signalStrength
    }
}

@ContributesMultibinding(scope = SingletonComponent::class)
class CellularMetricsCollector @Inject constructor(
    private val application: Application,
    private val signalStrengthProvider: CellularSignalStrengthProvider,
    private val metricsSettings: MetricsSettings,
    @AndroidSdkVersion private val androidSdkVersion: Int,
) : MetricCollector {
    private val report = Reporting.report()

    override suspend fun collect() {
        if (!metricsSettings.collectCellular) {
            return
        }
        try {
            collectCellularMetrics()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Logger.d("CellularMetricsCollector: Missing phone permission", e)
        } catch (e: Exception) {
            Logger.w("CellularMetricsCollector: Error collecting metrics", e)
        }
    }

    private fun collectCellularMetrics() {
        val tm = application.getSystemService(TelephonyManager::class.java) ?: return

        val networkOperator = tm.networkOperator
        if (!networkOperator.isNullOrBlank() && networkOperator.length >= 5) {
            val mcc = networkOperator.substring(0, 3)
            val mnc = networkOperator.substring(3)
            report.stringProperty(METRIC_MCC).update(mcc)
            report.stringProperty(METRIC_MNC).update(mnc)
        }

        if (hasPhoneStatePermission()) {
            collectNetworkType(tm)
        }

        report.stringProperty(METRIC_SIM_STATE).update(simStateToString(tm.simState))

        collectSignalStrength()
    }

    /**
     * getDataNetworkType() requires one of these. Bort Lite only gets READ_BASIC_PHONE_STATE, and
     * only from API 33 onwards: below that it has no way to read the network type.
     */
    private fun hasPhoneStatePermission(): Boolean = PHONE_STATE_PERMISSIONS.any {
        application.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun collectNetworkType(tm: TelephonyManager) {
        @Suppress("DEPRECATION")
        val networkType = tm.dataNetworkType
        report.stringProperty(METRIC_RAT).update(networkTypeToString(networkType))
    }

    private fun collectSignalStrength() {
        val signalStrength = signalStrengthProvider.get() ?: return

        report.distribution(METRIC_SIGNAL_LEVEL, aggregations = listOf(LATEST_VALUE))
            .record(signalStrength.level.toDouble())

        val dbm = signalStrengthDbm(signalStrength)
        if (dbm != null) {
            report.distribution(METRIC_SIGNAL_DBM, aggregations = listOf(LATEST_VALUE))
                .record(dbm.toDouble())
        }

        collectLteMetrics(signalStrength)
        collectNrMetrics(signalStrength)
    }

    @SuppressLint("NewApi")
    private fun signalStrengthDbm(signalStrength: android.telephony.SignalStrength): Int? {
        if (androidSdkVersion >= Build.VERSION_CODES.Q) {
            val cellSignal = signalStrength.getCellSignalStrengths()
                .firstOrNull { it.dbm in VALID_DBM_RANGE }
            if (cellSignal != null) return cellSignal.dbm
        }
        @Suppress("DEPRECATION")
        val gsmDbm = gsmAsuToDbm(signalStrength.gsmSignalStrength)
        if (gsmDbm != null) return gsmDbm
        @Suppress("DEPRECATION")
        val cdmaDbm = signalStrength.cdmaDbm
        if (cdmaDbm in VALID_DBM_RANGE) return cdmaDbm
        @Suppress("DEPRECATION")
        val evdoDbm = signalStrength.evdoDbm
        if (evdoDbm in VALID_DBM_RANGE) return evdoDbm
        return null
    }

    /**
     * Convert GSM ASU (Arbitrary Strength Unit) to dBm.
     *
     * ASU is an integer 0-31 reported by TelephonyManager.getGsmSignalStrength().
     * The standard conversion for GSM 850/900/1800/1900 MHz is:
     *
     *     dBm = 2 * asu - 113
     *
     * A value of 99 means "not detectable" and returns null.
     */
    private fun gsmAsuToDbm(asu: Int): Int? {
        if (asu in 0..31) {
            return 2 * asu - 113
        }
        return null
    }

    @SuppressLint("NewApi")
    private fun collectLteMetrics(signalStrength: android.telephony.SignalStrength) {
        if (androidSdkVersion >= Build.VERSION_CODES.P) {
            val lte = signalStrength.getCellSignalStrengths(CellSignalStrengthLte::class.java)
                .firstOrNull { it.rsrp != Int.MAX_VALUE || it.rsrq != Int.MAX_VALUE } ?: return
            val rsrp = lte.rsrp
            if (rsrp != Int.MAX_VALUE) {
                report.distribution(METRIC_LTE_RSRP, aggregations = listOf(LATEST_VALUE))
                    .record(rsrp.toDouble())
            }
            val rsrq = lte.rsrq
            if (rsrq != Int.MAX_VALUE) {
                report.distribution(METRIC_LTE_RSRQ, aggregations = listOf(LATEST_VALUE))
                    .record(rsrq.toDouble())
            }
        }
    }

    @SuppressLint("NewApi")
    private fun collectNrMetrics(signalStrength: android.telephony.SignalStrength) {
        if (androidSdkVersion >= Build.VERSION_CODES.Q) {
            val nr = signalStrength.getCellSignalStrengths(CellSignalStrengthNr::class.java)
                .firstOrNull { it.ssRsrp != Int.MAX_VALUE || it.ssRsrq != Int.MAX_VALUE } ?: return
            val rsrp = nr.ssRsrp
            if (rsrp != Int.MAX_VALUE) {
                report.distribution(METRIC_NR_RSRP, aggregations = listOf(LATEST_VALUE))
                    .record(rsrp.toDouble())
            }
            val rsrq = nr.ssRsrq
            if (rsrq != Int.MAX_VALUE) {
                report.distribution(METRIC_NR_RSRQ, aggregations = listOf(LATEST_VALUE))
                    .record(rsrq.toDouble())
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun networkTypeToString(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_GPRS -> "GPRS"
        TelephonyManager.NETWORK_TYPE_EDGE -> "EDGE"
        TelephonyManager.NETWORK_TYPE_UMTS -> "UMTS"
        TelephonyManager.NETWORK_TYPE_CDMA -> "CDMA"
        TelephonyManager.NETWORK_TYPE_EVDO_0 -> "EVDO_0"
        TelephonyManager.NETWORK_TYPE_EVDO_A -> "EVDO_A"
        TelephonyManager.NETWORK_TYPE_1xRTT -> "1XRTT"
        TelephonyManager.NETWORK_TYPE_HSDPA -> "HSDPA"
        TelephonyManager.NETWORK_TYPE_HSUPA -> "HSUPA"
        TelephonyManager.NETWORK_TYPE_HSPA -> "HSPA"
        TelephonyManager.NETWORK_TYPE_IDEN -> "IDEN"
        TelephonyManager.NETWORK_TYPE_EVDO_B -> "EVDO_B"
        TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
        TelephonyManager.NETWORK_TYPE_EHRPD -> "EHRPD"
        TelephonyManager.NETWORK_TYPE_HSPAP -> "HSPAP"
        TelephonyManager.NETWORK_TYPE_GSM -> "GSM"
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "TD_SCDMA"
        TelephonyManager.NETWORK_TYPE_IWLAN -> "IWLAN"
        NETWORK_TYPE_LTE_CA -> "LTE_CA"
        TelephonyManager.NETWORK_TYPE_NR -> "NR"
        else -> "UNKNOWN($type)"
    }

    private fun simStateToString(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_UNKNOWN -> "UNKNOWN"
        TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_REQUIRED"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_REQUIRED"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
        TelephonyManager.SIM_STATE_READY -> "READY"
        TelephonyManager.SIM_STATE_NOT_READY -> "NOT_READY"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "PERM_DISABLED"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "CARD_IO_ERROR"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "CARD_RESTRICTED"
        else -> "UNKNOWN($state)"
    }

    companion object {
        const val METRIC_MCC = "cellular_mcc"
        const val METRIC_MNC = "cellular_mnc"
        const val METRIC_RAT = "cellular_rat"
        const val METRIC_SIM_STATE = "cellular_sim_state"
        const val METRIC_SIGNAL_LEVEL = "cellular_signal_level"
        const val METRIC_SIGNAL_DBM = "cellular_signal_dbm"
        const val METRIC_LTE_RSRP = "cellular_lte_rsrp_dbm"
        const val METRIC_LTE_RSRQ = "cellular_lte_rsrq_db"
        const val METRIC_NR_RSRP = "cellular_nr_rsrp_dbm"
        const val METRIC_NR_RSRQ = "cellular_nr_rsrq_db"

        private val VALID_DBM_RANGE = -150..0

        // Neither of the platform-only permissions has an SDK constant.
        private val PHONE_STATE_PERMISSIONS = listOf(
            "android.permission.READ_PRIVILEGED_PHONE_STATE",
            "android.permission.READ_BASIC_PHONE_STATE",
            Manifest.permission.READ_PHONE_STATE,
        )
    }
}
