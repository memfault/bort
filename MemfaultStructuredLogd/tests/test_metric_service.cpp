#include <gtest/gtest.h>
#include <metric_reporter.h>
#include <metric_service.h>
#include "metric_test_utils.h"

#include <fstream>
#include <streambuf>

using namespace structured;

namespace {

TEST_F (MetricTest, HappyPathVersion1) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "Screen State",
             "aggregations": ["TIME_TOTALS", "UNKNOWN_AGGREGATION"],
             "value": "On",
             "newField": "newValue"
         }
)j"
    );
    service->addValue(
            R"j(
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "CPU Usage",
             "aggregations": ["MIN", "MAX", "MEAN"],
             "value": 6.34,
             "newField": "newValue"
         }
)j"
    );
    service->finishReport(
            R"j(
         {
            "version": 1,
            "timestampMs": 123456789,
            "reportType": "heartbeat",
            "newField": "newValue"
         }
)j"
    );

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "CPU Usage.min", "6.34", Double);
    assertMetricPresent("heartbeat", "CPU Usage.max", "6.34", Double);
    assertMetricPresent("heartbeat", "CPU Usage.mean", "6.34", Double);

    ASSERT_EQ(reportsJson[0], R"j({"version":1,"startTimestampMs":123456789,"endTimestampMs":123456789,"reportType":"heartbeat","metrics":{"CPU Usage.min":6.34,"CPU Usage.max":6.34,"CPU Usage.mean":6.34,"Screen State_On.total_secs":0}})j");


    // check hd reports
    struct stat hdStat;
    ASSERT_EQ(0, stat(hdReportTempPath.c_str(), &hdStat));

    std::ifstream t(hdReportTempPath);
    std::string str((std::istreambuf_iterator<char>(t)),
                    std::istreambuf_iterator<char>());
    ASSERT_EQ(str, R"j({"schema_version":1,"start_time":123456789,"duration_ms":0,"report_type":"heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"CPU Usage","metric_type":"gauge","data_type":"double","internal":false},"data":[{"t":123456789,"value":6.34}]},{"metadata":{"string_key":"Screen State","metric_type":"property","data_type":"string","internal":false},"data":[{"t":123456789,"value":"On"}]}]})j");

}

TEST_F (MetricTest, TypeConsistency) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "public_metric",
             "aggregations": ["MIN"],
             "value": 3
         }
)j"
    );
    service->addValue(
            R"j(
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "internal_metric",
             "internal": true,
             "aggregations": ["LATEST_VALUE"],
             "value": "a"
         }
)j"
    );

    service->finishReport(
            R"j(
         {
            "version": 1,
            "timestampMs": 123456789,
            "reportType": "heartbeat"
         }
)j"
    );

    assertCollectedReports(1);
    ASSERT_EQ(reportsJson[0], R"j({"version":1,"startTimestampMs":123456789,"endTimestampMs":123456789,"reportType":"heartbeat","metrics":{"public_metric.min":3},"internalMetrics":{"internal_metric.latest":"a"}})j");
}

TEST_F (MetricTest, DisabledByConfig) {
    Config::SharedPtr DISABLED_CONFIG = std::make_shared<TestingConfig>(false);
    auto service = std::make_unique<MetricService>(reporter, DISABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 3,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "Screen State",
             "aggregations": ["TIME_TOTALS", "UNKNOWN_AGGREGATION"],
             "value": "On",
             "newField": "newValue"
         }
)j"
    );
    service->addValue(
            R"j(
         {
             "version": 3,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "CPU Usage",
             "aggregations": ["MIN", "MAX", "MEAN"],
             "value": 6.34,
             "newField": "newValue"
         }
)j"
    );
    service->finishReport(
            R"j(
         {
            "version": 3,
            "timestampMs": 123456789,
            "reportType": "heartbeat",
            "newField": "newValue"
         }
)j"
    );

    assertCollectedReports(0);
}

TEST_F (MetricTest, BasicRollupTest) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 3,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "cpu_spiked",
             "aggregations": ["COUNT"],
             "dataType": "double",
             "metricType": "counter",
             "carryOver": false,
             "value": 1
         }
)j"
    );
    service->addValue(
            R"j(
         {
             "version": 3,
             "timestampMs": 123456790,
             "reportType": "heartbeat",
             "eventName": "cpu_spiked",
             "aggregations": ["COUNT"],
             "dataType": "double",
             "metricType": "counter",
             "carryOver": false,
             "value": 1
         }
)j"
    );
    service->addValue(
        R"j(
         {
             "version": 3,
             "timestampMs": 123456791,
             "reportType": "heartbeat",
             "eventName": "cpu_spiked",
             "aggregations": ["COUNT"],
             "dataType": "double",
             "metricType": "counter",
             "carryOver": false,
             "value": 1
         }
)j"
    );
    service->finishReport(
            R"j(
         {
            "version": 3,
            "timestampMs": 123456800,
            "reportType": "heartbeat"
         }
)j"
    );

    assertCollectedReports(1);

    // check hd reports
    struct stat hdStat;
    ASSERT_EQ(0, stat(hdReportTempPath.c_str(), &hdStat));

    std::ifstream t(hdReportTempPath);
    std::string str((std::istreambuf_iterator<char>(t)),
                    std::istreambuf_iterator<char>());
    ASSERT_EQ(str, R"j({"schema_version":1,"start_time":123456789,"duration_ms":11,"report_type":"heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"cpu_spiked","metric_type":"counter","data_type":"double","internal":false},"data":[{"t":123456789,"value":1.0},{"t":123456790,"value":1.0},{"t":123456791,"value":1.0}]}]})j");
}

TEST_F (MetricTest, HappyPathVersion2WithHd) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 2,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "screen_on",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": false
         }
)j"
    );

    service->addValue(
        R"j(
         {
             "version": 2,
             "timestampMs": 123456790,
             "reportType": "heartbeat",
             "eventName": "screen_on",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": true
         }
)j"
    );

    service->finishReport(
            R"j(
         {
            "version": 2,
            "timestampMs": 123456791,
            "reportType": "heartbeat"
         }
)j"
    );

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "screen_on.min", "0", structured::String);
    ASSERT_EQ(reportsJson[0], R"j({"version":1,"startTimestampMs":123456789,"endTimestampMs":123456791,"reportType":"heartbeat","metrics":{"screen_on.min":"0"}})j");

    // check hd reports
    struct stat hdStat;
    ASSERT_EQ(0, stat(hdReportTempPath.c_str(), &hdStat));

    std::ifstream t(hdReportTempPath);
    std::string str((std::istreambuf_iterator<char>(t)),
                    std::istreambuf_iterator<char>());
    ASSERT_EQ(str, R"j({"schema_version":1,"start_time":123456789,"duration_ms":2,"report_type":"heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"screen_on","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456789,"value":false},{"t":123456790,"value":true}]}]})j");

}

TEST_F (MetricTest, Version2CarryOver) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 2,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "screen_on",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": false
         }
)j"
    );

        service->addValue(
            R"j(
         {
             "version": 2,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "screen_on",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": false
         }
)j"
    );

    service->addValue(
        R"j(
         {
             "version": 2,
             "timestampMs": 123456790,
             "reportType": "heartbeat",
             "eventName": "screen_no_carry",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": false,
             "value": true
         }
)j"
    );

    service->addValue(
        R"j(
         {
             "version": 2,
             "timestampMs": 123456790,
             "reportType": "heartbeat",
             "eventName": "screen_on",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": true
         }
)j"
    );

    service->addValue(
        R"j(
         {
             "version": 2,
             "timestampMs": 123456791,
             "reportType": "heartbeat",
             "eventName": "screen_off",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": true
         }
)j"
    );

    service->finishReport(
            R"j(
         {
            "version": 2,
            "timestampMs": 123456791,
            "reportType": "heartbeat"
         }
)j"
    );

    service->finishReport(
            R"j(
         {
            "version": 2,
            "timestampMs": 123456792,
            "reportType": "heartbeat"
         }
)j"
    );

    assertCollectedReports(2);

    // check hd reports
    struct stat hdStat;
    ASSERT_EQ(0, stat(hdReportTempPath.c_str(), &hdStat));

    std::ifstream t(hdReportTempPath);
    std::string str((std::istreambuf_iterator<char>(t)),
                    std::istreambuf_iterator<char>());
    ASSERT_EQ(str, R"j({"schema_version":1,"start_time":123456791,"duration_ms":1,"report_type":"heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"screen_off","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456791,"value":true}]},{"metadata":{"string_key":"screen_on","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456791,"value":true}]}]})j");

}

TEST_F (MetricTest, Version2Types) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 2,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "screen_on",
             "aggregations": ["MIN"],
             "dataType": "boolean",
             "metricType": "counter",
             "carryOver": true,
             "value": false
         }
)j"
    );

        service->addValue(
            R"j(
         {
             "version": 2,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "screen_type",
             "aggregations": ["MIN"],
             "dataType": "string",
             "metricType": "counter",
             "carryOver": true,
             "value": "1"
         }
)j"
    );

    service->addValue(
        R"j(
         {
             "version": 2,
             "timestampMs": 123456790,
             "reportType": "heartbeat",
             "eventName": "screen_brightness",
             "aggregations": ["MIN"],
             "dataType": "double",
             "metricType": "counter",
             "carryOver": false,
             "value": 2.0
         }
)j"
    );

    service->finishReport(
            R"j(
         {
            "version": 2,
            "timestampMs": 123456791,
            "reportType": "heartbeat"
         }
)j"
    );

    assertCollectedReports(1);

    // check hd reports
    struct stat hdStat;
    ASSERT_EQ(0, stat(hdReportTempPath.c_str(), &hdStat));

    std::ifstream t(hdReportTempPath);
    std::string str((std::istreambuf_iterator<char>(t)),
                    std::istreambuf_iterator<char>());
    ASSERT_EQ(str, R"j({"schema_version":1,"start_time":123456789,"duration_ms":2,"report_type":"heartbeat","producer":{"version":"1","id":"structured_logd"},"rollups":[{"metadata":{"string_key":"screen_brightness","metric_type":"counter","data_type":"double","internal":false},"data":[{"t":123456790,"value":2.0}]},{"metadata":{"string_key":"screen_on","metric_type":"counter","data_type":"boolean","internal":false},"data":[{"t":123456789,"value":false}]},{"metadata":{"string_key":"screen_type","metric_type":"counter","data_type":"string","internal":false},"data":[{"t":123456789,"value":"1"}]}]})j");
}

TEST_F (MetricTest, TestAddMulti) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j([
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "Screen State",
             "aggregations": ["TIME_TOTALS", "UNKNOWN_AGGREGATION"],
             "value": "On",
             "newField": "newValue"
         },
        {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "CPU Usage",
             "aggregations": ["MIN", "MAX", "MEAN"],
             "value": 6.34,
             "newField": "newValue"
         }
])j"
    );
    service->finishReport(
            R"j(
         {
            "version": 1,
            "timestampMs": 123456789,
            "reportType": "heartbeat",
            "newField": "newValue"
         }
)j"
    );

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "CPU Usage.min", "6.34", Double);
    assertMetricPresent("heartbeat", "CPU Usage.max", "6.34", Double);
    assertMetricPresent("heartbeat", "CPU Usage.mean", "6.34", Double);

    ASSERT_EQ(reportsJson[0], R"j({"version":1,"startTimestampMs":123456789,"endTimestampMs":123456789,"reportType":"heartbeat","metrics":{"CPU Usage.min":6.34,"CPU Usage.max":6.34,"CPU Usage.mean":6.34,"Screen State_On.total_secs":0}})j");
}

TEST_F (MetricTest, MinMaxTypeCasting) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

    service->addValue(
            R"j(
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "CPU Usage",
             "aggregations": ["MIN", "MAX", "MEAN"],
             "value": 90,
             "newField": "newValue"
         }
)j"
    );
    service->addValue(
            R"j(
         {
             "version": 1,
             "timestampMs": 123456789,
             "reportType": "heartbeat",
             "eventName": "CPU Usage",
             "aggregations": ["MIN", "MAX", "MEAN"],
             "value": 100,
             "newField": "newValue"
         }
)j"
    );
    service->finishReport(
            R"j(
         {
            "version": 1,
            "timestampMs": 123456789,
            "reportType": "heartbeat",
            "newField": "newValue"
         }
)j"
    );

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "CPU Usage.min", "90", Uint64);
    assertMetricPresent("heartbeat", "CPU Usage.max", "100", Uint64);
    assertMetricPresent("heartbeat", "CPU Usage.mean", "95", Double);
}

}
