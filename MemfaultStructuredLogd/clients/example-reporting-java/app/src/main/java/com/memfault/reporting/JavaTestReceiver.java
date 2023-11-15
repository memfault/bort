package com.memfault.reporting;

import com.memfault.bort.java.reporting.Reporting;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class JavaTestReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
            Reporting.report()
                    .counter("reporting_java_test_metric")
                    .increment();
    }
}
