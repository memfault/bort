#include <gtest/gtest.h>
#include <storage.h>
#include <config.h>

using namespace structured;

#define SQLITE3_FILE ":memory:"

namespace {

TEST (ConfigTest, InitialDefaults) {
    StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(SQLITE3_FILE, "id");
    Config::SharedPtr config = std::make_shared<StoredConfig>(storage);

    // Check that defaults (i.e. document empty) or unparseable document return defaults
    for (int i = 0; i < 2; i++) {
        if (i == 1) {
            config->updateConfig("{[unparseable");
        }
        ASSERT_EQ(config->getDumpPeriodMs(), uint64_t(DUMP_PERIOD_MS));
        ASSERT_EQ(config->getRateLimiterConfig().msPerToken, uint64_t(RATE_LIMIT_PERIOD_MS));
        ASSERT_EQ(config->getRateLimiterConfig().initialCapacity, uint32_t(RATE_LIMIT_INITIAL_CAPACITY));
        ASSERT_EQ(config->getRateLimiterConfig().capacity, uint32_t(RATE_LIMIT_CAPACITY));
        ASSERT_EQ(config->getMaxMessageSize(), uint32_t(MAX_MESSAGE_SIZE_BYTES));
        ASSERT_EQ(config->getMinStorageThreshold(), uint64_t(MIN_STORAGE_THRESHOLD_BYTES));
        #ifdef BORT_UNDER_TEST
        // we hardcode this for testing
        ASSERT_EQ(config->getNumEventsBeforeDump(), 50u);
        #else
        ASSERT_EQ(config->getNumEventsBeforeDump(), uint32_t(NUM_EVENTS_BEFORE_DUMP));
        #endif
    }
}

TEST (ConfigTest, ConfigDocumentUpdate) {
    StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(SQLITE3_FILE, "id");
    Config::SharedPtr config = std::make_shared<StoredConfig>(storage);

    config->updateConfig(
            R"j(
         {
            "structured_log.dump_period_ms": 1234,
            "structured_log.max_message_size_bytes": 5678,
            "structured_log.min_storage_threshold_bytes": 536870912,
            "structured_log.num_events_before_dump": 9876,
            "structured_log.rate_limiting_settings": {
                "default_capacity": 123,
                "default_period_ms": 321,
                "max_buckets": 1
            }
         }
)j"
    );

    ASSERT_EQ(config->getDumpPeriodMs(), 1234u);
    ASSERT_EQ(config->getMaxMessageSize(), 5678u);
    ASSERT_EQ(config->getMinStorageThreshold(), 536870912u);
#ifdef BORT_UNDER_TEST
    // we hardcode this for testing
    ASSERT_EQ(config->getNumEventsBeforeDump(), 50u);
#else
    ASSERT_EQ(config->getNumEventsBeforeDump(), 9876u);
#endif
    ASSERT_EQ(config->getRateLimiterConfig().capacity, 123u);
    ASSERT_EQ(config->getRateLimiterConfig().msPerToken, 321u);
    ASSERT_EQ(config->isMetricReportEnabled(), true); // Testing the default
    ASSERT_EQ(config->isHighResMetricsEnabled(), false); // Testing the default
}

TEST (ConfigTest, MetricReportEnabledNonDefault) {
    StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(SQLITE3_FILE, "id");
    Config::SharedPtr config = std::make_shared<StoredConfig>(storage);

    config->updateConfig(
            R"j(
         {
            "structured_log.metric_report_enabled": true,
            "structured_log.high_res_metrics_enabled": true
         }
)j"
    );
    ASSERT_EQ(config->isMetricReportEnabled(), true);
    ASSERT_EQ(config->isHighResMetricsEnabled(), true);
}

}
