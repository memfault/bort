#include "reporting.h"

#include <cmath>
#include <functional>
#include <sstream>
#include <utility>

#include "rapidjson/ostreamwrapper.h"
#include "rapidjson/stringbuffer.h"
#include "rapidjson/writer.h"

static constexpr char kHeartbeat[] = "Heartbeat";
static constexpr char kSession[] = "Session";

namespace memfault {

static constexpr int kCurrentSchemaVersion = 2;
static constexpr char kVersion[] = "version";
static constexpr char kTimestampMs[] = "timestampMs";
static constexpr char kReportType[] = "reportType";
static constexpr char kReportName[] = "reportName";
static constexpr char kEventName[] = "eventName";
static constexpr char kInternal[] = "internal";
static constexpr char kAggregations[] = "aggregations";
static constexpr char kValue[] = "value";
static constexpr char kMetricType[] = "metricType";
static constexpr char kDataType[] = "dataType";
static constexpr char kCarryOver[] = "carryOver";

Metric::Metric(const std::shared_ptr<StructuredLogger> &logger,
               std::string name,
               const std::string &reportType,
               const std::string &reportName,
               std::vector<AggregationType> aggregations,
               MetricType metricType,
               DataType dataType,
               bool carryOverValue,
               bool internal
)
    : mLogger(logger),
      mName(std::move(name)),
      mReportType(reportType),
      mReportName(reportName),
      mAggregations{std::move(aggregations)},
      mMetricType(metricType),
      mDataType(dataType),
      mCarryOverValue(carryOverValue),
      mInternal(internal) {}

static void addAggregationToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer, AggregationType type);
static void addMetricTypeToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer,
                                  MetricType metricType);
static void addDataTypeToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer,
                                DataType dataType);

static void addWithValueHook(
  const std::shared_ptr<StructuredLogger> &logger,
  uint64_t timestamp,
  const std::string &reportType,
  const std::string &reportName,
  const std::string &eventName,
  bool internal,
  const std::vector<AggregationType> &aggregations,
  MetricType metricType,
  DataType dataType,
  bool carryOverValue,
  const std::function<void(rapidjson::Writer<rapidjson::OStreamWrapper> &)> &valueHook
) {
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

  if (!reportName.empty()) {
    writer.Key(kReportName);
    writer.String(reportName.c_str());
  }

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

  writer.Key(kMetricType);
  addMetricTypeToWriter(writer, metricType);

  writer.Key(kDataType);
  addDataTypeToWriter(writer, dataType);

  writer.Key(kCarryOver);
  writer.Bool(carryOverValue);

  writer.EndObject();
  oStreamWrapper.Flush();

  logger->addValue(strStream.str());
}

static void startReport(
        const std::shared_ptr<StructuredLogger> &logger,
        uint64_t timestamp,
        const std::string &reportType,
        const std::string &reportName) {
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

    writer.Key(kReportName);
    writer.String(reportName.c_str());

    writer.EndObject();
    oStreamWrapper.Flush();

    logger->startReport(strStream.str());
}

static void finishReport(
        const std::shared_ptr<StructuredLogger> &logger,
        uint64_t timestamp,
        const std::string &reportType,
        const std::string &reportName) {
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

    writer.Key(kReportName);
    writer.String(reportName.c_str());

    writer.EndObject();
    oStreamWrapper.Flush();

    logger->finishReport(strStream.str());
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

static void addMetricTypeToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer,
                                  MetricType metricType) {
    switch (metricType) {
        case COUNTER:
            writer.String("counter");
            break;
        case GAUGE:
            writer.String("gauge");
            break;
        case PROPERTY:
            writer.String("property");
            break;
        case EVENT:
            writer.String("event");
            break;
    }
}

static void addDataTypeToWriter(rapidjson::Writer<rapidjson::OStreamWrapper> &writer,
                                DataType dataType) {
    switch (dataType) {
        case DOUBLE:
            writer.String("double");
            break;
        case STRING:
            writer.String("string");
            break;
        case BOOLEAN:
            writer.String("boolean");
            break;
    }
}

void Metric::addValue(uint64_t timestamp, const std::string &stringVal) const {
    addWithValueHook(mLogger,
                     timestamp,
                     mReportType,
                     mReportName,
                     mName,
                     mInternal,
                     mAggregations,
                     mMetricType,
                     mDataType,
                     mCarryOverValue,
                     [&stringVal](rapidjson::Writer<rapidjson::OStreamWrapper> &writer) {
                       writer.String(stringVal.c_str());
                     });
}

void Metric::addValue(uint64_t timestamp, double doubleVal) const {
    addWithValueHook(mLogger,
                     timestamp,
                     mReportType,
                     mReportName,
                     mName,
                     mInternal,
                     mAggregations,
                     mMetricType,
                     mDataType,
                     mCarryOverValue,
                     [&doubleVal](rapidjson::Writer<rapidjson::OStreamWrapper> &writer) {
                       writer.Double(doubleVal);
                     });
}

void Metric::addValue(uint64_t timestamp, bool boolVal) const {
    addWithValueHook(
      mLogger,
      timestamp,
      mReportType,
      mReportName,
      mName,
      mInternal,
      mAggregations,
      mMetricType,
      mDataType,
      mCarryOverValue,
      [&boolVal](rapidjson::Writer<rapidjson::OStreamWrapper> &writer) { writer.Bool(boolVal); });
}

uint64_t Metric::timestamp() const {
    return getTimeInMsSinceEpoch();
}

Report::Report() : mReportType(kHeartbeat), mLogger(std::make_shared<StructuredLogger>()) {}
Report::Report(std::string name) : mReportType(kSession), mReportName(std::move(name)),
    mLogger(std::make_shared<StructuredLogger>()) {}

Report::~Report() {}
}

uint64_t memfault::Report::timestamp() const {
    return getTimeInMsSinceEpoch();
}

void memfault::Report::startSession() const {
    startSession(getTimeInMsSinceEpoch());
}

void memfault::Report::startSession(uint64_t timestamp) const {
    startReport(mLogger, timestamp, mReportType, mReportName);
}

void memfault::Report::finishSession() const {
    finishSession(getTimeInMsSinceEpoch());
}

void memfault::Report::finishSession(uint64_t timestamp) const {
    finishReport(mLogger, timestamp, mReportType, mReportName);
}

metric_report_t metric_report_new() {
    return new memfault::Report();
}

void metric_report_destroy(metric_report_t self) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    delete r;
}

metric_report_t metric_session_start(const char* name) {
    auto r = new memfault::Report(name);
    auto logger = r->logger();
    startReport(logger, getTimeInMsSinceEpoch(), r->reportType(), r->reportName());
    return r;
}

void metric_session_finish(metric_report_t self) {
    metric_session_finish_plus_ts(self, 0);
}

void metric_session_finish_plus_ts(metric_report_t self, uint64_t ts) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    auto logger = r->logger();
    finishReport(logger, getTimeInMsSinceEpoch() + ts, r->reportType(), r->reportName());
    metric_report_destroy(self);
}

metric_counter_t metric_report_counter(metric_report_t self, const char *name, bool sumInReport) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    return r->counter(name, sumInReport).release();
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

metric_string_prop_t metric_report_string_prop(metric_report_t self, const char *name,
                                               bool addLatestToReport) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    return r->stringProperty(name, addLatestToReport).release();
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

metric_number_prop_t metric_report_number_prop(metric_report_t self, const char *name,
                                               bool addLatestToReport) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    return r->numberProperty(name, addLatestToReport).release();
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

metric_string_state_tracker_t metric_report_string_state_tracker(metric_report_t self,
                                                                 const char *name,
                                                                 enum AggregationType *aggregations,
                                                                 size_t n_aggregations) {
    auto *r = reinterpret_cast<memfault::Report*>(self);
    std::vector<AggregationType> aggregationsVector(aggregations, aggregations + n_aggregations);
    return r->stringStateTracker(name, aggregationsVector).release();
}

void metric_string_state_tracker_destroy(metric_string_state_tracker_t self) {
    auto *s = reinterpret_cast<memfault::StateTracker<std::string> *>(self);
    delete s;
}

void metric_string_state_tracker_state(metric_string_state_tracker_t self, const char *value) {
    auto *s = reinterpret_cast<memfault::StateTracker<std::string> *>(self);
    s->state(value);
}

void metric_string_state_tracker_state_with_ts(metric_string_state_tracker_t self,
                                               const char *value, uint64_t ts) {
    auto *s = reinterpret_cast<memfault::StateTracker<std::string> *>(self);
    s->state(value, ts);
}

metric_event_t metric_report_event(metric_report_t self, const char *name, bool countInReport) {
    auto *r = reinterpret_cast<memfault::Report *>(self);
    return r->event(name, countInReport).release();
}

void metric_event_destroy(metric_event_t self) {
    auto *s = reinterpret_cast<memfault::Event *>(self);
    delete s;
}

void metric_event_add(metric_event_t self, const char *value) {
    auto *s = reinterpret_cast<memfault::Event *>(self);
    s->add(value);
}

void metric_event_add_with_ts(metric_event_t self, const char *value, uint64_t ts) {
    auto *s = reinterpret_cast<memfault::Event *>(self);
    s->add(value, ts);
}

uint64_t getTimeInMsSinceEpoch() {
    timespec spec{};
    clock_gettime(CLOCK_REALTIME, &spec);

    uint64_t epoch = spec.tv_sec;
    uint32_t ms = round(spec.tv_nsec / 1.0e6);
    if (ms > 999) {
        epoch++;
        ms = 0;
    }
    return epoch * 1000 + ms;
}
