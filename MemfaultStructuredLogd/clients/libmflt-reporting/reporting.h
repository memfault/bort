#pragma once

#ifdef __cplusplus
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "structuredlog.h"
#else
#include <stdbool.h>
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

enum MetricType {
    COUNTER,
    GAUGE,
    PROPERTY,
    EVENT,
};

enum DataType {
    DOUBLE,
    STRING,
    BOOLEAN,
};

#ifdef __cplusplus
}
#endif

#ifdef __cplusplus
namespace memfault {

class Metric {
protected:
    Metric(
            const std::shared_ptr<StructuredLogger> &logger,
            std::string name,
            const std::string &reportType,
            const std::string &reportName,
            std::vector<AggregationType> aggregations,
            MetricType metricType,
            DataType dataType,
            bool carryOverValue,
            bool internal
   );

    void addValue(uint64_t timestamp, const std::string &stringVal) const;
    void addValue(uint64_t timestamp, double numberVal) const;
    void addValue(uint64_t timestamp, bool boolVal) const;

    uint64_t timestamp() const;

private:
    std::shared_ptr<StructuredLogger> mLogger;
    const std::string mName;
    const std::string &mReportType;
    const std::string &mReportName;
    std::vector<AggregationType> mAggregations;
    MetricType mMetricType;
    DataType mDataType;
    bool mCarryOverValue;
    bool mInternal;
};

std::vector<AggregationType> const COUNTER_AGGS_SUM { SUM };
std::vector<AggregationType> const COUNTER_AGGS_NO_SUM { };

class Counter : protected Metric {
public:
    Counter(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            const std::string &reportName,
            bool sumInReport,
            bool internal
    ) : Metric(
            logger,
            name,
            reportType,
            reportName,
            sumInReport ? COUNTER_AGGS_SUM : COUNTER_AGGS_NO_SUM,
            COUNTER,
            DOUBLE,
            false,
            internal
    ) {}

public:
    template<typename T>
    void incrementBy(T by, uint64_t timestamp) const {
        addValue(timestamp, (double) by);
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
            const std::string &reportName,
            const std::vector<AggregationType>& aggregations,
            bool internal
    ) : Metric(
            logger,
            name,
            reportType,
            reportName,
            aggregations,
            GAUGE,
            DOUBLE,
            false,
            internal
    ) {}

    template<typename T>
    void record(T value) const {
        record(value, timestamp());
    }

    template<typename T>
    void record(T value, uint64_t timestamp) const {
        addValue(timestamp, value);
    }
};

std::vector<AggregationType> const PROPERTY_AGGS_LATEST { LATEST_VALUE };
std::vector<AggregationType> const PROPERTY_AGGS_NO_LATEST { };

template<typename T>
class Property : protected Metric {
public:
    Property(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            const std::string &reportName,
            DataType dataType,
            bool addLatestToReport,
            bool internal
    ) : Metric(
            logger,
            name,
            reportType,
            reportName,
            addLatestToReport ? PROPERTY_AGGS_LATEST : PROPERTY_AGGS_NO_LATEST,
            PROPERTY,
            dataType,
            true,
            internal
    ) {}

    void update(const T &value) const {
        update(value, timestamp());
    }

    void update(const T &value, uint64_t timestamp) const {
        addValue(timestamp, value);
    }
};

template<typename T>
class StateTracker : protected Metric {
public:
    StateTracker(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            const std::string &reportName,
            const std::vector<AggregationType>& aggregations,
            DataType dataType,
            bool internal
    ) : Metric(
            logger,
            name,
            reportType,
            reportName,
            aggregations,
            PROPERTY,
            dataType,
            true,
            internal
    ) {}

    void state(const T &value) const {
        state(value, timestamp());
    }

    void state(const T &value, uint64_t timestamp) const {
        addValue(timestamp, value);
    }
};

std::vector<AggregationType> const EVENT_AGGS_COUNT { COUNT };
std::vector<AggregationType> const EVENT_AGGS_NO_COUNT { };

class Event : protected Metric {
public:
    Event(
            const std::shared_ptr<StructuredLogger> &logger,
            const std::string &name,
            const std::string &reportType,
            const std::string &reportName,
            bool countInReport,
            bool internal
    ) : Metric(
            logger,
            name,
            reportType,
            reportName,
            countInReport ? EVENT_AGGS_COUNT : EVENT_AGGS_NO_COUNT,
            EVENT,
            STRING,
            false,
            internal
    ) {}

    void add(const std::string &value) const {
        add(value, timestamp());
    }

    void add(const std::string &value, uint64_t timestamp) const {
        addValue(timestamp, value);
    }
};


class Report {
public:
    explicit Report();
    explicit Report(std::string name);

    ~Report();

    [[nodiscard]] uint64_t timestamp() const;

    /**
     * Aggregates the total count at the end of the period.
     */
    [[nodiscard]] std::unique_ptr<Counter> counter(const std::string &name, bool sumInReport = true, bool internal = false) const {
        return std::make_unique<Counter>(
                mLogger,
                name,
                mReportType,
                mReportName,
                sumInReport,
                internal
        );
    }

    /**
     * Keeps track of a distribution of the values recorded during the period.
     *
     * One metric will be generated for each [aggregations].
     */
    [[nodiscard]] std::unique_ptr<Distribution> distribution(
            const std::string &name,
            const std::vector<AggregationType>& aggregations,
            bool internal = false
    ) const {
        return std::make_unique<Distribution>(
                mLogger,
                name,
                mReportType,
                mReportName,
                aggregations,
                internal
        );
    }

    /**
     * Keep track of the latest value of a string property.
     */
    [[nodiscard]] std::unique_ptr<Property<std::string>> stringProperty(
            const std::string &name,
            bool addLatestToReport = true,
            bool internal = false
    ) const {
        return std::make_unique<Property<std::string>>(
                mLogger,name,
                mReportType,
                mReportName,
                STRING,
                addLatestToReport,
                internal
        );
    }

    /**
     * Keep track of the latest value of a number property.
     */
    [[nodiscard]] std::unique_ptr<Property<double>> numberProperty(
            const std::string &name,
            bool addLatestToReport = true,
            bool internal = false
    ) const {
        return std::make_unique<Property<double>>(
                mLogger,
                name,
                mReportType,
                mReportName,
                DOUBLE,
                addLatestToReport,
                internal
        );
    }

    /**
     * Tracks total time spent in each state during the report period.
     */
    [[nodiscard]] std::unique_ptr<StateTracker<std::string>> stringStateTracker(
            const std::string &name,
            const std::vector<AggregationType>& aggregations,
            bool internal = false
    ) const {
        return std::make_unique<StateTracker<std::string>>(
                mLogger,
                name,
                mReportType,
                mReportName,
                aggregations,
                STRING,
                internal
        );
    }

    /**
     * Tracks total time spent in each state during the report period.
     */
    [[nodiscard]] std::unique_ptr<StateTracker<bool>> boolStateTracker(
            const std::string &name,
            const std::vector<AggregationType>& aggregations,
            bool internal = false
    ) const {
        return std::make_unique<StateTracker<bool>>(
                mLogger,
                name,
                mReportType,
                mReportName,
                aggregations,
                BOOLEAN,
                internal
        );
    }

    /**
     * Track individual events. Replacement for Custom Events.
     */
    [[nodiscard]] std::unique_ptr<Event> event(const std::string &name, bool countInReport = false, bool internal = false) const {
        return std::make_unique<Event>(
                mLogger,
                name,
                mReportType,
                mReportName,
                countInReport,
                internal
        );
    }

    /**
     * Finishes the Report. The Report should no longer be used after this call.
     */
    void finish() const;

    /**
     * Retrieves the structured logger. Private use for starting and finishing sessions.
     */
    [[nodiscard]] std::shared_ptr<StructuredLogger> logger() const {
        return mLogger;
    }

    /**
     * Retrieves the structured logger. Private use for starting and finishing sessions.
     */
    [[nodiscard]] std::string reportType() const {
        return mReportType;
    }

    /**
     * Retrieves the structured logger. Private use for starting and finishing sessions.
     */
    [[nodiscard]] std::string reportName() const {
        return mReportName;
    }

private:
    const std::string mReportType;
    const std::string mReportName;
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
typedef void* metric_string_state_tracker_t;
typedef void* metric_event_t;

/**
 * Creates a heartbeat report.
 */
metric_report_t metric_report_new();
/**
 * Deallocates a report.
 */
void metric_report_destroy(metric_report_t self);
/**
 * Starts a session report.
 */
metric_report_t metric_session_start(const char* name);
/**
 * Finishes and then deallocates a session report.
 */
void metric_session_finish(metric_report_t self);
/**
 * Finishes and then deallocates a session report, ADDING ts to the timestamp.
 *
 * Not meant for general use.
 */
void metric_session_finish_plus_ts(metric_report_t self, uint64_t ts);

/**
 * Aggregates the total count at the end of the period.
 */
metric_counter_t metric_report_counter(metric_report_t self, const char* name, bool sumInReport);
void metric_counter_destroy(metric_counter_t self);
void metric_counter_increment(metric_counter_t self);
void metric_counter_increment_with_ts(metric_counter_t self, uint64_t ts);
void metric_counter_increment_by(metric_counter_t self, int value);
void metric_counter_increment_by_with_ts(metric_counter_t self, int value, uint64_t ts);

/**
 * Keeps track of a distribution of the values recorded during the period.
 *
 * One metric will be generated for each [aggregations].
 */
metric_distribution_t metric_report_distribution(metric_report_t self, const char* name, enum AggregationType* aggregations, size_t n_aggregations);
void metric_distribution_destroy(metric_distribution_t self);
void metric_distribution_record_str(metric_report_t self, const char *value);
void metric_distribution_record_str_with_ts(metric_report_t self, const char *value, uint64_t ts);
void metric_distribution_record_double(metric_report_t self, double value);
void metric_distribution_record_double_with_ts(metric_report_t self, double value, uint64_t ts);

/**
 * Keep track of the latest value of a string property.
 */
metric_string_prop_t metric_report_string_prop(metric_report_t self, const char* name, bool addLatestToReport);
void metric_string_prop_destroy(metric_string_prop_t self);
void metric_string_prop_update(metric_string_prop_t self, const char* value);
void metric_string_prop_update_with_ts(metric_string_prop_t self, const char* value, uint64_t ts);

/**
 * Keep track of the latest value of a number property.
 */
metric_number_prop_t metric_report_number_prop(metric_report_t self, const char* name, bool addLatestToReport);
void metric_number_prop_destroy(metric_number_prop_t self);
void metric_number_prop_update(metric_number_prop_t self, double value);
void metric_number_prop_update_with_ts(metric_number_prop_t self, double value, uint64_t ts);

/**
 * Tracks total time spent in each state during the report period.
 */
metric_string_state_tracker_t metric_report_string_state_tracker(metric_report_t self, const char* name, enum AggregationType* aggregations, size_t n_aggregations);
void metric_string_state_tracker_destroy(metric_string_state_tracker_t self);
void metric_string_state_tracker_state(metric_string_state_tracker_t self, const char *value);
void metric_string_state_tracker_state_with_ts(metric_string_state_tracker_t self, const char *value, uint64_t ts);

/**
 * Track individual events. Replacement for Custom Events.
 */
metric_event_t metric_report_event(metric_report_t self, const char* name, bool countInReport);
void metric_event_destroy(metric_event_t self);
void metric_event_add(metric_event_t self, const char *value);
void metric_event_add_with_ts(metric_event_t self, const char *value, uint64_t ts);

/**
 * Obtains the time in milliseconds since epoch. This time is not
 * monotonic and can increase/decrease based NTP / user setup.
 *
 * @return The number of milliseconds since epoch.
 */
uint64_t getTimeInMsSinceEpoch();

#ifdef __cplusplus
}
#endif
