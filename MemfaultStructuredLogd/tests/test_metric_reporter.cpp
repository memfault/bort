#include <gtest/gtest.h>
#include <metric_reporter.h>
#include "metric_test_utils.h"

using namespace structured;

namespace {

TEST_F (MetricTest, HappyPathNumerics) {
    reporter->addValue(
            1, "heartbeat", 2345, "cpu_load", false, {"MIN", "MAX", "SUM", "MEAN", "COUNT", "LATEST_VALUE"}, "1", Int64
    );
    reporter->addValue(
            1, "heartbeat", 3000, "cpu_load", false, {"MIN", "MAX", "SUM", "MEAN", "COUNT", "LATEST_VALUE"}, "2", Int64
    );
    reporter->finishReport(1u, "heartbeat", 6789, false);

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "cpu_load MIN", "1", Int64);
    assertMetricPresent("heartbeat", "cpu_load MAX", "2", Int64);
    assertMetricPresent("heartbeat", "cpu_load SUM", "3", Int64);
    assertMetricPresent("heartbeat", "cpu_load MEAN", "1.5", Double);
}

TEST_F (MetricTest, HappyPathTimetotals) {
    reporter->addValue(
            1, "heartbeat", 2345, "screen", false, {"TIME_TOTALS"}, "on", String
    );
    reporter->addValue(
            1, "heartbeat", 3345, "screen", false, {"TIME_TOTALS"}, "off", String
    );
    reporter->addValue(
            1, "heartbeat", 4345, "screen", false, {"TIME_TOTALS"}, "on", String
    );
    reporter->addValue(
            1, "heartbeat", 6345, "screen", false, {"TIME_TOTALS"}, "off", String
    );
    reporter->finishReport(1u, "heartbeat", 7345, false);

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "screen Time Total on secs", "3", Uint64);
    assertMetricPresent("heartbeat", "screen Time Total off secs", "2", Uint64);
}

TEST_F (MetricTest, TimePerHour) {
    const auto hour = 1000*60*60;
    reporter->addValue(
            1, "heartbeat", 1634074357043 + hour, "screen", false, {"TIME_PER_HOUR"}, "on", String
    );
    reporter->addValue(
            1, "heartbeat", 1634074357043 + 3 * hour, "screen", false, {"TIME_PER_HOUR"}, "off", String
    );
    reporter->finishReport(1u, "heartbeat", 1634074357043u + 4*hour, false);

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "screen Time Per Hour on secs/hour", "2400", Double);
    assertMetricPresent("heartbeat", "screen Time Per Hour off secs/hour", "1200", Double);
}

TEST_F (MetricTest, FinishNonExistingReport) {
    reporter->finishReport(1, "bogus", 12345, false);
    assertCollectedReports(0);
}

TEST_F (MetricTest, MultipleReportsCrossContamination) {
    reporter->addValue(1, "a", 67890, "metric_a", false, { "SUM" }, "1", Int64);
    reporter->addValue(1, "b", 67890, "metric_b", false, { "SUM" }, "1", Int64);
    reporter->finishReport(1, "a", 98765, false);

    assertCollectedReports(1);
    ASSERT_EQ(reports[0].metrics.size(), 1);
    ASSERT_EQ(std::get<0>(reports[0].metrics[0]), "metric_a SUM");
    ASSERT_EQ(std::get<1>(reports[0].metrics[0]), false);
    ASSERT_EQ(std::get<2>(reports[0].metrics[0]), "1");
    ASSERT_EQ(std::get<3>(reports[0].metrics[0]), Int64);
}

TEST_F (MetricTest, TwoLastValueMetrics) {
    reporter->addValue(1, "a", 67890, "metric_a", false, { "LATEST_VALUE" }, "1", Int64);
    reporter->addValue(1, "a", 67890, "metric_b", false, { "LATEST_VALUE" }, "1", Int64);
    reporter->finishReport(1, "a", 98765, false);

    assertCollectedReports(1);
    ASSERT_EQ(reports[0].metrics.size(), 2);
    ASSERT_EQ(std::get<0>(reports[0].metrics[0]), "metric_a Latest Value");
    ASSERT_EQ(std::get<0>(reports[0].metrics[1]), "metric_b Latest Value");
}

TEST_F (MetricTest, StartNextReport){
    reporter->addValue(1, "rolling_report", 67890, "metric_a", false, { "LATEST_VALUE" }, "1", Int64);
    reporter->finishReport(1, "rolling_report", 98765, true);
    assertCollectedReports(1);

    reporter->addValue(1, "rolling_report", 100000, "metric_b", false, { "LATEST_VALUE" }, "1", Int64);
    reporter->finishReport(1, "rolling_report", 100000, false);

    assertCollectedReports(2);
    // Check that the start of the second report is actually the finish timestamp of the first
    ASSERT_EQ(reports[0].finishTimestamp, reports[1].startTimestamp);

    reporter->addValue(1, "rolling_report", 500000, "metric_b", false, { "LATEST_VALUE" }, "1", Int64);
    reporter->finishReport(1, "rolling_report", 600000, true);
    // Also check that if the report was not autostarted, the initial timestamp is the one with the first metric
    assertCollectedReports(3);
    ASSERT_EQ(reports[2].startTimestamp, 500000);

    reporter->finishReport(1, "rolling_report", 700000, true);
    // Even if autostarted, a report with no metrics should not be produced.
    assertCollectedReports(3);

    // Even if the previous autostarted report did not produce metrics, it should have updated the timestamp
    reporter->addValue(1, "rolling_report", 800000, "metric_b", false, { "LATEST_VALUE" }, "1", Int64);
    reporter->finishReport(1, "rolling_report", 900000, true);
    assertCollectedReports(4);
    ASSERT_EQ(reports[3].startTimestamp, 700000);
}

}
