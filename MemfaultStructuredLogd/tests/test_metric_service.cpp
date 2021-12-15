#include <gtest/gtest.h>
#include <metric_reporter.h>
#include <metric_service.h>
#include "metric_test_utils.h"

using namespace structured;

namespace {

class TestingConfig : public Config {
public:
    TestingConfig(bool enabled) : enabled(enabled) {}
    ~TestingConfig() override {}

    void updateConfig(const std::string &config) override {}

    RateLimiterConfig getRateLimiterConfig() override {
        return RateLimiterConfig{
                .initialCapacity = 1,
                .capacity = 1,
                .msPerToken = 1
        };
    }

    uint64_t getDumpPeriodMs() override {
        return 1;
    }

    uint32_t getNumEventsBeforeDump() override {
        return 1;
    }

    uint32_t getMaxMessageSize() override {
        return 1;
    }

    uint64_t getMinStorageThreshold() override {
        return 1;
    }

    bool isMetricReportEnabled() override {
        return enabled;
    }

private:
    bool enabled;
};

TEST_F (MetricTest, HappyPathVersion1) {
    Config::SharedPtr ENABLED_CONFIG = std::make_shared<TestingConfig>(true);
    auto service = std::make_unique<MetricService>(reporter, ENABLED_CONFIG);

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

    assertCollectedReports(1);
    assertMetricPresent("heartbeat", "CPU Usage MIN", "6.34", Double);
    assertMetricPresent("heartbeat", "CPU Usage MAX", "6.34", Double);
    assertMetricPresent("heartbeat", "CPU Usage MEAN", "6.34", Double);

    ASSERT_EQ(reportsJson[0], R"j({"version":1,"startTimestampMs":123456789,"endTimestampMs":123456789,"reportType":"heartbeat","metrics":{"CPU Usage MIN":6.34,"CPU Usage MAX":6.34,"CPU Usage MEAN":6.34,"Screen State Time Total On secs":0}})j");
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
    ASSERT_EQ(reportsJson[0], R"j({"version":1,"startTimestampMs":123456789,"endTimestampMs":123456789,"reportType":"heartbeat","metrics":{"public_metric MIN":3},"internalMetrics":{"internal_metric Latest Value":"a"}})j");
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

}
