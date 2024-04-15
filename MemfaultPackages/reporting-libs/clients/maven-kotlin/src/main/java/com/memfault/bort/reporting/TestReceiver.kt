package com.memfault.bort.reporting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.memfault.bort.reporting.NumericAgg.COUNT
import com.memfault.bort.reporting.NumericAgg.MAX
import com.memfault.bort.reporting.NumericAgg.MEAN
import com.memfault.bort.reporting.NumericAgg.MIN
import com.memfault.bort.reporting.NumericAgg.SUM
import com.memfault.bort.reporting.StateAgg.TIME_PER_HOUR
import com.memfault.bort.reporting.StateAgg.TIME_TOTALS

class TestReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            "com.memfault.intent.action.LOG_METRIC" -> {
                val numericAggs = listOf(COUNT, NumericAgg.LATEST_VALUE, MAX, MEAN, MIN, SUM)
                val stateAggs = listOf(TIME_TOTALS, TIME_PER_HOUR, StateAgg.LATEST_VALUE)

                Reporting.report()
                    .counter("reporting-maven-kotlin-counter")
                    .increment()
                Reporting.report()
                    .sync()
                    .record(true)
                Reporting.report()
                    .successOrFailure("reporting-maven-kotlin-sf")
                    .record(false)
                Reporting.report()
                    .distribution("reporting-maven-kotlin-dist", numericAggs)
                    .record(100)
                Reporting.report()
                    .stringStateTracker("reporting-maven-kotlin-sst", stateAggs)
                    .state("stated")
                Reporting.report()
                    .boolStateTracker("reporting-maven-kotlin-bst", stateAggs)
                    .state(false)
                Reporting.report()
                    .stringProperty("reporting-maven-kotlin-sp")
                    .update("record")
                Reporting.report()
                    .numberProperty("reporting-maven-kotlin-np")
                    .update(200L)
                Reporting.report()
                    .event("reporting-maven-kotlin-event", true)
                    .add("evented")
            }
        }
    }
}
