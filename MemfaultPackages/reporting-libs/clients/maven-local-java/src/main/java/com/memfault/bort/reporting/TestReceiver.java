package com.memfault.bort.reporting;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.memfault.bort.java.reporting.Distribution;
import com.memfault.bort.java.reporting.Reporting;
import java.util.List;

import static com.memfault.bort.reporting.NumericAgg.COUNT;
import static com.memfault.bort.reporting.NumericAgg.MAX;
import static com.memfault.bort.reporting.NumericAgg.MEAN;
import static com.memfault.bort.reporting.NumericAgg.MIN;
import static com.memfault.bort.reporting.NumericAgg.SUM;
import static com.memfault.bort.reporting.StateAgg.TIME_PER_HOUR;
import static com.memfault.bort.reporting.StateAgg.TIME_TOTALS;
import static java.util.Arrays.asList;

public class TestReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) {
      return;
    }

    if ("com.memfault.intent.action.LOG_METRIC".equals(intent.getAction())) {

      List<NumericAgg> numericAggs = asList(COUNT, NumericAgg.LATEST_VALUE, MAX, MEAN, MIN, SUM);
      List<StateAgg> stateAggs = asList(TIME_TOTALS, TIME_PER_HOUR, StateAgg.LATEST_VALUE);

      Reporting.report()
          .counter("reporting-maven-local-java-counter")
          .increment();
      Reporting.report()
          .sync(true)
          .record(true);
      Reporting.report()
          .successOrFailure("reporting-maven-local-java-sf")
          .record(false);
      Reporting.report()
          .distribution("reporting-maven-local-java-dist", numericAggs)
          .record(100.0);
      Reporting.report()
          .stringStateTracker("reporting-maven-local-java-sst", stateAggs)
          .state("stated");
      Reporting.report()
          .boolStateTracker("reporting-maven-local-java-bst", stateAggs)
          .state(false);
      Reporting.report()
          .stringProperty("reporting-maven-local-java-sp", true)
          .update("record");
      Reporting.report()
          .numberProperty("reporting-maven-local-java-np", true)
          .update(200L);
      Reporting.report()
          .event("reporting-maven-local-java-event", true, true)
          .add("evented");

      Reporting.startSession("reporting-maven-local-session");

      Distribution session = Reporting.session("reporting-maven-local-session")
          .distribution("reporting-maven-local-session-dist", numericAggs);
      session.record(2000L);
      session.record(8000L);

      Reporting.finishSession("reporting-maven-local-session");
    }
  }
}
