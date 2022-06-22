#pragma once

#ifdef __cplusplus
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include <structuredlog.h>

#endif

#ifdef __cplusplus
extern "C" {
#endif
enum AggregationType {
    MIN,
    MAX,
    SUM,
    MEAN,
    COUNT,
    TIME_TOTALS,
    TIME_PER_HOUR,
    LATEST_VALUE
};
#ifdef __cplusplus
}
#endif

#ifdef __cplusplus
namespace memfault {

class Metric {
protected:
    Metric(const std::shared_ptr<StructuredLogger> &logger, std::string name, const std::string &reportType,
           std::vector<AggregationType> aggregations, bool internal);

    void add(uint64_t timestamp, const std::string &stringVal) const;
    void add(uint64_t timestamp, double numberVal) const;

    uint64_t timestamp() const;

private:
    std::shared_ptr<StructuredLogger> mLogger;
    const std::string mName;
    const std::string &mReportType;
    std::vector<AggregationType> mAggregations;
    bool mInternal;
};


class Counter : protected Metric {
public:
    Counter(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            bool internal
    ) : Metric(logger, name, reportType, {SUM}, internal) {}

public:
    template<typename T>
    void incrementBy(T by, uint64_t timestamp) const {
        add(timestamp, (double) by);
    }

    template<typename T>
    void incrementBy(T by = 1) const {
        incrementBy(by, timestamp());
    }

    void increment() const {
        incrementBy(1);
    }

    void increment(uint64_t timestamp) const {
        incrementBy(1, timestamp);
    }
};


class Distribution : protected Metric {
public:
    Distribution(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            const std::vector<AggregationType>& aggregations,
            bool internal
    ) : Metric(logger, name, reportType, aggregations, internal) {}

    template<typename T>
    void record(T value) const {
        record(value, timestamp());
    }

    template<typename T>
    void record(T value, uint64_t timestamp) const {
        add(timestamp, value);
    }
};


template<typename T>
class Property : protected Metric {
public:
    Property(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            bool internal
    ) : Metric(logger, name, reportType, {LATEST_VALUE}, internal) {}

    void update(const T &value) const {
        update(value, timestamp());
    }

    void update(const T &value, uint64_t timestamp) const {
        add(timestamp, value);
    }
};

class StateTracker : protected Metric {
public:
    StateTracker(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            const std::vector<AggregationType>& aggregations,
            bool internal
    ) : Metric(logger, name, reportType, aggregations, internal) {}

    void state(const std::string &value) const {
        state(value, timestamp());
    }

    void state(const std::string &value, uint64_t timestamp) const {
        add(timestamp, value);
    }
};


class Report {
public:
    explicit Report(std::string reportType);

    ~Report();

    [[nodiscard]] std::unique_ptr<Counter> counter(const std::string &name, bool internal = false) const {
        return std::make_unique<Counter>(mLogger, name, mReportType, internal);
    }

    [[nodiscard]] std::unique_ptr<Distribution> distribution(const std::string &name, const std::vector<AggregationType>& aggregations,
                                               bool internal = false) const {
        return std::make_unique<Distribution>(mLogger, name, mReportType, aggregations, internal);
    }

    [[nodiscard]] std::unique_ptr<Property<std::string>> stringProperty(const std::string &name, bool internal = false) const {
        return std::make_unique<Property<std::string>>(mLogger, name, mReportType, internal);
    }

    [[nodiscard]] std::unique_ptr<Property<double>> numberProperty(const std::string &name, bool internal = false) const {
        return std::make_unique<Property<double>>(mLogger, name, mReportType, internal);
    }

    [[nodiscard]] std::unique_ptr<StateTracker> stateTracker(const std::string &name, const std::vector<AggregationType>& aggregations,
                                                             bool internal = false) const {
        return std::make_unique<StateTracker>(mLogger, name, mReportType, aggregations, internal);
    }

    void finish(bool startNextReport = false) const;
    void finish(uint64_t timestamp, bool startNextReport = false) const;

private:
    const std::string mReportType;
    std::shared_ptr<StructuredLogger> mLogger;
};

}
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef enum AggregationType metric_aggregation_t;
typedef void* metric_report_t;
typedef void* metric_counter_t;
typedef void* metric_distribution_t;
typedef void* metric_string_prop_t;
typedef void* metric_number_prop_t;
typedef void* metric_state_tracker_t;

metric_report_t metric_report_new(const char* name);
void metric_report_destroy(metric_report_t self);
void metric_report_finish(metric_report_t self);
void metric_report_finish_and_start_new(metric_report_t self);
void metric_report_finish_with_ts(metric_report_t self, uint64_t ts);

metric_counter_t metric_report_counter(metric_report_t self, const char* name);
void metric_counter_destroy(metric_counter_t self);
void metric_counter_increment(metric_counter_t self);
void metric_counter_increment_with_ts(metric_counter_t self, uint64_t ts);
void metric_counter_increment_by(metric_counter_t self, int value);
void metric_counter_increment_by_with_ts(metric_counter_t self, int value, uint64_t ts);

metric_distribution_t metric_report_distribution(metric_report_t self, const char* name, enum AggregationType* aggregations, size_t n_aggregations);
void metric_distribution_destroy(metric_distribution_t self);
void metric_distribution_record_str(metric_report_t self, const char *value);
void metric_distribution_record_str_with_ts(metric_report_t self, const char *value, uint64_t ts);
void metric_distribution_record_double(metric_report_t self, double value);
void metric_distribution_record_double_with_ts(metric_report_t self, double value, uint64_t ts);

metric_string_prop_t metric_report_string_prop(metric_report_t self, const char* name);
void metric_string_prop_destroy(metric_string_prop_t self);
void metric_string_prop_update(metric_string_prop_t self, const char* value);
void metric_string_prop_update_with_ts(metric_string_prop_t self, const char* value, uint64_t ts);

metric_number_prop_t metric_report_number_prop(metric_report_t self, const char* name);
void metric_number_prop_destroy(metric_number_prop_t self);
void metric_number_prop_update(metric_number_prop_t self, double value);
void metric_number_prop_update_with_ts(metric_number_prop_t self, double value, uint64_t ts);

metric_state_tracker_t metric_report_state_tracker(metric_report_t self, const char* name, enum AggregationType* aggregations, size_t n_aggregations);
void metric_state_tracker_destroy(metric_state_tracker_t self);
void metric_state_tracker_state(metric_state_tracker_t self, const char *value);
void metric_state_tracker_state_with_ts(metric_state_tracker_t self, const char *value, uint64_t ts);

#ifdef __cplusplus
}
#endif
