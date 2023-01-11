#include <gtest/gtest.h>
#include <storage.h>
#include <config.h>
#include <logger.h>
#include <dumper.h>
#include <thread>
#include <gmock/gmock.h>

using namespace structured;

#define SQLITE3_FILE ":memory:"

namespace {

static const int MAX_MESSAGE_SIZE = 512;
static uint64_t MIN_STORAGE_THRESHOLD = kInMemoryAvailableSpace / 2;
static const int DUMP_AFTER_EVENTS = 50;
static const int TOKEN_CAPACITY = 5;
static const int INITIAL_TOKENS = 5;
static const int MS_PER_TOKEN = 1;
static const bool METRICS_ENABLED = true;

class TestingConfig : public Config {
public:
    ~TestingConfig() override {

    }

    void updateConfig(const std::string &config) override {}

    RateLimiterConfig getRateLimiterConfig() override {
        return RateLimiterConfig{
                .initialCapacity = INITIAL_TOKENS,
                .capacity = TOKEN_CAPACITY,
                .msPerToken = MS_PER_TOKEN
        };
    }

    uint64_t getDumpPeriodMs() override {
        return 1;
    }

    uint32_t getNumEventsBeforeDump() override {
        return DUMP_AFTER_EVENTS;
    }

    uint32_t getMaxMessageSize() override {
        return MAX_MESSAGE_SIZE;
    }

    uint64_t getMinStorageThreshold() override {
        return MIN_STORAGE_THRESHOLD;
    }

    bool isMetricReportEnabled() override {
        return METRICS_ENABLED;
    }

    bool isHighResMetricsEnabled() override {
        return HIGH_RES_METRICS_ENABLED;
    }
};

class MockDumper : public Dumper {
public:
    MockDumper(
            std::string dumpFile,
            std::shared_ptr<Config> &config,
            std::shared_ptr<StorageBackend> &backend,
            std::function<bool(int, std::string)> handleDump,
            std::function<bool()> isReadyForDump,
            uint64_t dumpPeriod,
            bool dumpOldEntriesOnBoot = true,
            bool dumpImmediately = false /* for testing */
    ) : Dumper(dumpFile, config, backend, handleDump, isReadyForDump, dumpPeriod, dumpOldEntriesOnBoot, dumpImmediately) {}

    ~MockDumper() override {}

    MOCK_METHOD0(triggerDump, void());
};

class LoggerTest : public ::testing::Test {
public:
    std::shared_ptr<StorageBackend> storage;
    std::shared_ptr<MockDumper> mockDumper;
    std::unique_ptr<Logger> logger;
    uint64_t now = 0;
protected:
    void SetUp() override {
        StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(
                SQLITE3_FILE,
                "id"
        );

        MIN_STORAGE_THRESHOLD = kInMemoryAvailableSpace / 2;
        Config::SharedPtr config = std::make_shared<TestingConfig>();
        this->storage = storage;

        this->mockDumper = std::make_shared<MockDumper>(
                "/dev/null",
                config,
                storage,
                [&](int nEvents, const std::string &dumpPath) -> bool {
                    return true;
                }, [&]() -> bool { return true; },
                uint64_t(1000),
                false,
                false);


        auto rateLimiter = std::make_unique<TokenBucketRateLimiter>(config->getRateLimiterConfig(), [&]() {
            return now;
        });

        // Make the compiler typechecker happy by passing a known type rather than the mock type.
        Dumper::SharedPtr loggerDumper = this->mockDumper;
        this->logger = std::make_unique<Logger>(storage, loggerDumper, config, rateLimiter);
    }
};

TEST_F (LoggerTest, TestRateLimiter) {
    for (uint32_t i = 0; i < 10; i++) {
        logger->log(i, "tag", "{}");
    }

    uint32_t dumpCount = 0;
    storage->dump(false, [&](BootIdDumpView &dumpView) {
        dumpView.forEachEvent([&](const LogEntry &entry) {
            dumpCount++;
        });
        return true;
    });

    ASSERT_EQ(5u, dumpCount);
}

TEST_F (LoggerTest, TestDumpAfterEvents) {
    EXPECT_CALL(*mockDumper.get(), triggerDump())
            .Times(1);

    for (uint32_t i = 0; i < DUMP_AFTER_EVENTS; i++) {
        now = i;
        logger->log(i, "tag", "{}");
    }
}

TEST_F (LoggerTest, TestMaxMessageSizeWrapping) {
    std::stringstream buffer;
    buffer << '"';
    for (int i = 0; i < 1000; i++) {
        buffer << "e";
    }
    buffer << '"';
    logger->log(1u, "type", buffer.str());

    storage->dump(false, [&](BootIdDumpView &dumpView) {
        dumpView.forEachEvent([&](const LogEntry &entry) {
            ASSERT_EQ(entry.timestamp, 1u);
            ASSERT_EQ(entry.type, "oversized_data");
            ASSERT_EQ(entry.blob, R"j({"original_type":"type","size":1002})j");
        });
    });
}

TEST_F (LoggerTest, TestMinStorageThreshold) {
    logger->log(1u, "tag", "{}");
    MIN_STORAGE_THRESHOLD = kInMemoryAvailableSpace * 2u;
    logger->log(2u, "tag_ignored_no_space", "{}");

    uint32_t dumpCount = 0;
    storage->dump(false, [&](BootIdDumpView &dumpView) {
        dumpView.forEachEvent([&](const LogEntry &entry) {
            dumpCount++;
        });
        return true;
    });

    ASSERT_EQ(1u, dumpCount);
}

}
