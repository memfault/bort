#pragma once

#define SQLITE3_FILE ":memory:"

namespace structured {

class MetricTest : public ::testing::Test {
public:
    std::shared_ptr<StoredReporter> reporter;
    std::vector<Report> reports;
    std::vector<std::string> reportsJson;
protected:
    void SetUp() override {
        StorageBackend::SharedPtr storage = std::make_shared<Sqlite3StorageBackend>(
                SQLITE3_FILE,
                "id"
        );

        this->reporter = std::make_shared<StoredReporter>(
                storage,
                [&](const Report &report, const std::string &reportJson) {
                    reports.push_back(report);
                    reportsJson.push_back(reportJson);
                },
                std::make_unique<TokenBucketRateLimiter>(
                        RateLimiterConfig{
                                .initialCapacity = 1,
                                .capacity = 1,
                                .msPerToken = 1000
                        },
                        []{return 0;}
                )
        );
    }

    void assertCollectedReports(int expectedCount) {
        ASSERT_EQ((unsigned int) expectedCount, reports.size());
    }

    void assertMetricPresent(const std::string& type, const std::string &metric, const std::string &expectedValue,
                             MetricValueType expectedType, bool expectedInternal = false) {
        for (auto &report : reports) {
            if (report.type == type) {
                for (auto &m : report.metrics) {
                    std::string name;
                    bool internal;
                    std::string value;
                    MetricValueType valueType;
                    std::tie(name, internal, value, valueType) = m;
                    if (name == metric) {
                        if (value != expectedValue) {
                            std::cerr << "expected metric with type=" << type << " name=" << metric << " to have value="
                                      << expectedValue << " but actual value=" << value << std::endl;
                            FAIL();
                        } else if (valueType != expectedType) {
                            std::cerr << "expected metric with type=" << type << " name=" << metric << " to have type="
                                      << expectedType << " but actual type=" << valueType << std::endl;
                            FAIL();
                        } else if (internal != expectedInternal) {
                            std::cerr << "expected metric with type=" << type << " name=" << metric << " as internal="
                                      << expectedInternal << " but actual internal=" << internal << std::endl;
                            FAIL();
                        } else { return; }
                    }
                }
                // Metric wasn't found, so fail
                std::cerr << "expected metric with type=" << type << " name=" << metric << " to have value="
                          << expectedValue << " but it was not present";
                FAIL();
            }
        }

        // Report wasn't found, so fail
        FAIL();
    }
};

}
