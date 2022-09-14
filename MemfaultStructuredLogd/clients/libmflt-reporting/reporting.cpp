#include <sstream>
#include <functional>
#include <utility>
#include "reporting.h"

#include "rapidjson/ostreamwrapper.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"

namespace memfault {

static constexpr int kCurrentSchemaVersion = 1;
static constexpr char kVersion[] = "version";
static constexpr char kTimestampMs[] = "timestampMs";
static constexpr char kReportType[] = "reportType";
static constexpr char kStartNextReport[] = "startNextReport";

static constexpr char kEventName[] = "eventName";
static constexpr char kInternal[] = "internal";
static constexpr char kAggregations[] = "aggregations";
static constexpr char kValue[] = "value";

Metric::Metric(const std::shared_ptr<StructuredLogger> &logger, std::string name, const std::string &reportType,
               std::vector<AggregationType> aggregations, bool internal) :
               mLogger(logger), mName(std::move(name)), mReportType(reportType), mAggregations{std::move(aggregations)}, mInternal(internal)
               {}

static void addAggregationToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer, AggregationType type);

static void addWithValueHook(const std::shared_ptr<StructuredLogger> &logger,
                             uint64_t timestamp,
                             const std::string &reportType,
                             const std::string &eventName,
                             bool internal,
                             const std::vector<AggregationType> &aggregations,
                             const std::function<void(rapidjson::Writer<rapidjson::OStreamWrapper>&)> &valueHook) {
    std::ostringstream strStream;
    rapidjson::OStreamWrapper oStreamWrapper(strStream);
    rapidjson::Writer<rapidjson::OStreamWrapper> writer(oStreamWrapper);

    writer.StartObject();

    writer.Key(kVersion);
    writer.Int(kCurrentSchemaVersion);

    writer.Key(kTimestampMs);
    writer.Uint64(timestamp);

    writer.Key(kReportType);
    writer.String(reportType.c_str());

    writer.Key(kEventName);
    writer.String(eventName.c_str());

    if (internal) {
        writer.Key(kInternal);
        writer.Bool(true);
    }

    writer.Key(kAggregations);
    writer.StartArray();
    for (AggregationType agg : aggregations) {
        addAggregationToWriter(writer, agg);
    }
    writer.EndArray();

    writer.Key(kValue);
    valueHook(writer);

    writer.EndObject();
    oStreamWrapper.Flush();

    logger->addValue(strStream.str());
}

static void addAggregationToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer, AggregationType type) {
    switch (type) {
        case SUM:
            writer.String("SUM");
            break;
        case MIN:
            writer.String("MIN");
            break;
        case MAX:
            writer.String("MAX");
            break;
        case MEAN:
            writer.String("MEAN");
            break;
        case COUNT:
            writer.String("COUNT");
            break;
        case TIME_TOTALS:
            writer.String("TIME_TOTALS");
            break;
        case TIME_PER_HOUR:
            writer.String("TIME_PER_HOUR");
            break;
        case LATEST_VALUE:
            writer.String("LATEST_VALUE");
            break;
    }
}

void
Metric::add(uint64_t timestamp, const std::string &stringVal) const {
    addWithValueHook(mLogger, timestamp, mReportType, mName, mInternal, mAggregations, [&stringVal](rapidjson::Writer<rapidjson::OStreamWrapper> &writer) {
        writer.String(stringVal.c_str());
    });
}

void
Metric::add(uint64_t timestamp, double doubleVal) const {
    addWithValueHook(mLogger, timestamp, mReportType, mName, mInternal, mAggregations, [&doubleVal](rapidjson::Writer<rapidjson::OStreamWrapper> &writer) {
        writer.Double(doubleVal);
    });
}

uint64_t Metric::timestamp() const {
    return mLogger->timestamp() / 1000;
}

Report::Report(std::string reportType) : mReportType(std::move(reportType)), mLogger(std::make_shared<StructuredLogger>()) {
}

Report::~Report() {

}

void Report::finish(uint64_t ts, bool startNextReport) const {
    std::ostringstream strStream;
    rapidjson::OStreamWrapper oStreamWrapper(strStream);
    rapidjson::Writer<rapidjson::OStreamWrapper> writer(oStreamWrapper);

    writer.StartObject();

    writer.Key(kVersion);
    writer.Int(kCurrentSchemaVersion);

    writer.Key(kTimestampMs);
    writer.Uint64(ts);

    writer.Key(kReportType);
    writer.String(mReportType.c_str());

    if (startNextReport) {
        writer.Key(kStartNextReport);
        writer.Bool(true);
    }

    writer.EndObject();
    oStreamWrapper.Flush();

    mLogger->finishReport(strStream.str());
}

void Report::finish(bool startNextReport) const {
    finish(mLogger->timestamp(), startNextReport);
}

}

metric_report_t metric_report_new(const char* name) {
    return new memfault::Report(name);
}

void metric_report_destroy(metric_report_t self) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    delete r;
}

void metric_report_finish(metric_report_t self) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    r->finish();
}

void metric_report_finish_and_start_new(metric_report_t self) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    r->finish(true);
}

void metric_report_finish_with_ts(metric_report_t self, uint64_t ts) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    r->finish(ts);
}

void metric_report_finish_with_ts_and_start_new(metric_report_t self, uint64_t ts) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    r->finish(ts, true);
}

metric_counter_t metric_report_counter(metric_report_t self, const char* name) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    return r->counter(name).release();
}

void metric_counter_destroy(metric_counter_t self) {
    auto *c = reinterpret_cast<memfault::Counter*>(self);
    delete c;
}

void metric_counter_increment(metric_counter_t self) {
    auto *c = reinterpret_cast<memfault::Counter*>(self);
    c->increment();
}

void metric_counter_increment_with_ts(metric_counter_t self, uint64_t ts) {
    auto *c = reinterpret_cast<memfault::Counter*>(self);
    c->increment(ts);
}

void metric_counter_increment_by(metric_counter_t self, int value) {
    auto *c = reinterpret_cast<memfault::Counter*>(self);
    c->incrementBy(value);
}

void metric_counter_increment_by_with_ts(metric_counter_t self, int value, uint64_t ts) {
    auto *c = reinterpret_cast<memfault::Counter*>(self);
    c->incrementBy(value, ts);
}

metric_distribution_t metric_report_distribution(metric_report_t self, const char* name, enum AggregationType* aggregations, size_t n_aggregations) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    std::vector<AggregationType> aggregationsVector(aggregations, aggregations + n_aggregations);
    return r->distribution(name, aggregationsVector).release();
}

void metric_distribution_destroy(metric_distribution_t self) {
    auto *r = reinterpret_cast<memfault::Distribution*>(self);
    delete r;
}

void metric_distribution_record_str(metric_distribution_t self, const char *value) {
    auto *r = reinterpret_cast<memfault::Distribution*>(self);
    r->record(value);
}

void metric_distribution_record_str_with_ts(metric_distribution_t self, const char *value, uint64_t ts) {
    auto *r = reinterpret_cast<memfault::Distribution*>(self);
    r->record(value, ts);
}

void metric_distribution_record_double(metric_distribution_t self, double value) {
    auto *r = reinterpret_cast<memfault::Distribution*>(self);
    r->record(value);
}

void metric_distribution_record_double_with_ts(metric_distribution_t self, double value, uint64_t ts) {
    auto *r = reinterpret_cast<memfault::Distribution*>(self);
    r->record(value, ts);
}

metric_string_prop_t metric_report_string_prop(metric_report_t self, const char* name) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    return r->stringProperty(name).release();
}

void metric_string_prop_destroy(metric_string_prop_t self) {
    auto *s = reinterpret_cast<memfault::Property<std::string>*>(self);
    delete s;
}

void metric_string_prop_update(metric_string_prop_t self, const char* value) {
    auto *s = reinterpret_cast<memfault::Property<std::string>*>(self);
    s->update(value);
}

void metric_string_prop_update_with_ts(metric_string_prop_t self, const char* value, uint64_t ts) {
    auto *s = reinterpret_cast<memfault::Property<std::string>*>(self);
    s->update(value, ts);
}

metric_number_prop_t metric_report_number_prop(metric_report_t self, const char* name) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    return r->numberProperty(name).release();
}

void metric_number_prop_destroy(metric_number_prop_t self) {
    auto *n = reinterpret_cast<memfault::Property<double>*>(self);
    delete n;
}

void metric_number_prop_update(metric_number_prop_t self, double value) {
    auto *n = reinterpret_cast<memfault::Property<double>*>(self);
    n->update(value);
}

void metric_number_prop_update_with_ts(metric_number_prop_t self, double value, uint64_t ts) {
    auto *n = reinterpret_cast<memfault::Property<double>*>(self);
    n->update(value, ts);
}

metric_state_tracker_t metric_report_state_tracker(metric_report_t self, const char* name, enum AggregationType* aggregations, size_t n_aggregations) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    std::vector<AggregationType> aggregationsVector(aggregations, aggregations + n_aggregations);
    return r->stateTracker(name, aggregationsVector).release();
}

void metric_state_tracker_destroy(metric_state_tracker_t self) {
    auto *s = reinterpret_cast<memfault::StateTracker*>(self);
    delete s;
}

void metric_state_tracker_state(metric_state_tracker_t self, const char *value) {
    auto *s = reinterpret_cast<memfault::StateTracker*>(self);
    s->state(value);
}

void metric_state_tracker_state_with_ts(metric_state_tracker_t self, const char *value, uint64_t ts) {
    auto *s = reinterpret_cast<memfault::StateTracker*>(self);
    s->state(value, ts);
}
