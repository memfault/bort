#include "storage.h"

#include <cstring>
#include <fstream>
#include <memory>
#include <utility>

#include <sys/statvfs.h>
#include <unordered_map>

#include "log.h"

namespace structured {

enum versions {
  INITIAL = 0,
  LOG_TABLE,
  CONFIG,
  REPORTS,
  REPORTS_META_TABLE,
  METRIC_METADATA,
  MAX,
};

void SqliteDatabase::migrate() {
    *this << "PRAGMA foreign_keys = ON";
    const int version = getDbVersion();
    switch (version) {
        case INITIAL:
            *this <<
                  "CREATE TABLE log("
                  "  timestamp int,"
                  "  type text,"
                  "  blob text,"
                  "  bootRowId int,"
                  "  internal int"
                  ")";
            *this <<
                "CREATE TABLE boot_ids("
                "  id integer primary key autoincrement,"
                "  uuid text unique"
                ")";
            *this <<
                "CREATE TABLE cids("
                "  cid text,"
                "  next_cid text"
                ")";
            setDbVersion(LOG_TABLE);
            [[clang::fallthrough]];
        case LOG_TABLE:
            *this <<
                "CREATE TABLE config("
                "  content text"
                ")";
            setDbVersion(CONFIG);
            [[clang::fallthrough]];
        case CONFIG:
            *this <<
                "CREATE TABLE report_metric("
                "  eventName text,"
                "  type text,"
                "  internal int,"
                "  version int,"
                "  timestamp int,"
                "  aggregations int,"
                "  value,"
                "  valueType int"
                ")";
            *this << "CREATE INDEX report_metric_type on report_metric(type)";
            *this << "CREATE INDEX report_metric_timestamp on report_metric(timestamp)";
            *this << "CREATE INDEX report_metric_type_event on report_metric(type, eventName)";
            setDbVersion(REPORTS);
            [[clang::fallthrough]];
        case REPORTS:
            *this <<
                "CREATE TABLE report("
                "  type text PRIMARY KEY,"
                "  startTimestamp int"
                ")";
            setDbVersion(REPORTS_META_TABLE);
            [[clang::fallthrough]];
        case REPORTS_META_TABLE:
            *this <<
                "CREATE TABLE metric_metadata("
                "  reportType text,"
                "  eventName text,"
                "  metricType text,"
                "  dataType text,"
                "  carryOver int default 0,"
                "  aggregations int,"
                "  internal int,"
                "  valueType int,"
                "  PRIMARY KEY (reportType, eventName)"
                ")";
            *this << "CREATE INDEX metric_metadata_carryOver on metric_metadata(carryOver) where carryOver = 1";

            // sqlite does not support alter table drop column and
            // losing data is acceptable, drop the table and recreate
            *this << "DROP INDEX report_metric_type";
            *this << "DROP INDEX report_metric_timestamp";
            *this << "DROP INDEX report_metric_type_event";
            *this << "DROP TABLE report_metric";

            *this <<
                "CREATE TABLE report_metric("
                "  eventName text,"
                "  type text,"
                "  version int,"
                "  timestamp int,"
                "  value,"
                "  FOREIGN KEY(type, eventName) REFERENCES metric_metadata(reportType, eventName)"
                ")";
            *this << "CREATE INDEX report_metric_type on report_metric(type)";
            *this << "CREATE INDEX report_metric_timestamp on report_metric(timestamp)";
            *this << "CREATE INDEX report_metric_type_event on report_metric(type, eventName)";

            setDbVersion(METRIC_METADATA);
            [[clang::fallthrough]];
        case METRIC_METADATA:
            break;
        default:
            throw Sqlite3Exception("invalid db version", version);
    }

    if (getDbVersion() != MAX -1) {
        throw Sqlite3Exception("migrations failed", version);
    }
}

Sqlite3StorageBackend::Sqlite3StorageBackend(
        const std::string &path,
        const std::string &bootId
)
        : _db(path),
          _insertStmt(_db << "INSERT INTO log (timestamp, type, blob, bootRowId, internal) VALUES(?, ?, ?, ?, ?)"),
          _metricLastValueStmt(_db << "SELECT value "
                                      "FROM report_metric "
                                      "WHERE eventName = ? AND type = ?"
                                      "ORDER BY timestamp DESC "
                                      "LIMIT 1"),
          _metricUpsertMetadataStmt(_db << "INSERT OR REPLACE INTO metric_metadata"
                                           "(reportType, eventName, metricType, dataType, carryOver, aggregations, internal, valueType) "
                                           "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"),
          _metricInsertStmt(_db << "INSERT INTO report_metric"
                                   "(eventName, type, version, timestamp, value) "
                                   "VALUES(?, ?, ?, ?, ?)"),
          _metricSelectAllStmt(_db << "SELECT timestamp, value "
                                      "FROM report_metric "
                                      "WHERE eventName = ? AND type = ? "
                                      "ORDER BY timestamp ASC"),
          _reportInsertMetadata(_db << "INSERT OR IGNORE INTO report"
                                       "(type, startTimestamp) "
                                       "VALUES(?,?)"),
          _path(path),
          _inMemory(path == ":memory:") {
    registerBoot(bootId);
    ensureCids();
    ensureConfig();
}

int SqliteDatabase::getDbVersion() {
  int version;
  *this << "pragma user_version" >> version;
  return version;
}

void SqliteDatabase::setDbVersion(int version) {
  // can't bind pragma values in prepared statements
  std::ostringstream ostream;
  ostream << "pragma user_version = " << version;
  *this << ostream.str();
}

SqliteDatabase::SqliteDatabase(
        const std::string &path
) : sqlite::database(path) {
    migrate();
}

void Sqlite3StorageBackend::store(const LogEntry &entry) {
  std::unique_lock<std::recursive_mutex> lock(dbMutex);
  _insertStmt.reset();
  _insertStmt << entry.timestamp;
  _insertStmt << entry.type;
  _insertStmt << entry.blob;
  _insertStmt << bootIdRow;
  _insertStmt << (entry.internal ? 1 : 0);
  _insertStmt.execute();
}


void Sqlite3StorageBackend::dump(bool skipLatest, std::function<void(BootIdDumpView &)> callback){
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    auto query = skipLatest ?
                 "SELECT id, uuid FROM boot_ids WHERE id < (SELECT max(id) FROM boot_ids) ORDER BY id ASC" :
                 "SELECT id, uuid FROM boot_ids ORDER BY id ASC";

    _db << query >> [&](int64_t rowId, const std::string &uuid) {
        Sqlite3BootIdDumpView dumpView(*this, uuid, rowId);
        callback(dumpView);
        _db << "DELETE FROM log WHERE bootRowId = ?" << rowId;
    };
    _db << "DELETE FROM boot_ids WHERE id < (SELECT max(id) FROM boot_ids)";

    int64_t entries = 0;
    _db << "SELECT COUNT(*) FROM log" >> entries;
    if (entries == 0) {
        for (const auto &storageEmptyListener : storageEmptyListeners) {
            storageEmptyListener();
        }
    }
}

void Sqlite3StorageBackend::registerBoot(const std::string &bootId) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    int64_t lastId = -1;
    std::string lastUuid;

    _db << "SELECT id, uuid from boot_ids ORDER BY id DESC LIMIT 1" >> [&](int64_t id, const std::string& uuid) {
        lastId = id;
        lastUuid = uuid;
    };

    if (lastId == -1 || lastUuid != bootId) {
        _db << "INSERT INTO boot_ids (uuid) VALUES (?)" << bootId;
        lastId = _db.last_insert_rowid();
    }
    bootIdRow = lastId;
}

void Sqlite3StorageBackend::ensureCids() {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    int64_t count;
    _db << "SELECT count(*) FROM cids" >> count;
    if (count == 0) {
        _db << "INSERT INTO cids (cid, next_cid) VALUES(?, ?)" << generateCid() << generateCid();
    }
}

std::string Sqlite3StorageBackend::generateCid() {
    std::ifstream ifs("/proc/sys/kernel/random/uuid");
    std::string cid;
    ifs >> cid;
    return cid;
}

void Sqlite3StorageBackend::consumeCid() {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    _db << "UPDATE cids SET cid = next_cid, next_cid = ?" << generateCid();
}

std::pair<std::string, std::string> Sqlite3StorageBackend::getCidPair() {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    std::string cid, nextCid;
    _db << "SELECT cid, next_cid FROM cids LIMIT 1" >> std::tie(cid, nextCid);
    return std::make_pair(cid, nextCid);
}

void Sqlite3StorageBackend::addStorageEmtpyListener(OnStorageEmptyListener listener) {
    storageEmptyListeners.emplace_back(listener);
}

void Sqlite3StorageBackend::ensureConfig() {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    int64_t count;
    _db << "SELECT count(*) FROM config" >> count;
    if (count == 0) {
        _db << "INSERT INTO config (content) VALUES(?)" << "";
    }
}

std::string Sqlite3StorageBackend::getConfig() {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    std::string config;
    _db << "SELECT content FROM config LIMIT 1" >> config;
    return config;
}

void Sqlite3StorageBackend::setConfig(const std::string &config) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    _db << "UPDATE config SET content = ?" << config;
}

uint64_t Sqlite3StorageBackend::getAvailableSpace() {
    // Return a fixed amount for in-memory databases used in testing
    if (_inMemory) return kInMemoryAvailableSpace;
    struct statvfs64 stat{};
    if (statvfs64(_path.c_str(), &stat)) {
        ALOGE("Unable to compute available space for %s: %s", _path.c_str(), strerror(errno));
        return 0u;
    }
    return uint64_t(stat.f_bavail * stat.f_bsize);
}

static std::string asString(double value) {
    std::ostringstream strstream;
    strstream << value;
    return strstream.str();
}

std::unique_ptr<Report> Sqlite3StorageBackend::finishReport(
        uint8_t version,
        const std::string &type,
        int64_t endTimestamp,
        bool startNextReport,
        bool includeHdMetrics,
        std::function<void(const ReportMetadata&)> reportMetadataCallback,
        std::function<void(const MetricDetailedView&)> metricDetailedViewCallback) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);

    int count = 0;
    int64_t startTimestamp = 0;
    _db << "SELECT startTimestamp, COUNT(m.timestamp) FROM report r, report_metric m WHERE r.type = m.type and r.type = ?" << type >> [&](int64_t startTs, int num) {
        startTimestamp = startTs;
        count = num;
    };
    if (count == 0) {
        removeReportData(type);
        if (startNextReport) {
            ensureReportMetadataLocked(type, endTimestamp);
        }
        ALOGW("Ignored attempt to finish a report of type %s with no metrics", type.c_str());
        return nullptr;
    }

    if (includeHdMetrics) {
        reportMetadataCallback(ReportMetadata{type, startTimestamp, endTimestamp});
    }

    auto report = collectMetricsLocked(
        version,
        type,
        startTimestamp,
        endTimestamp,
        includeHdMetrics,
        metricDetailedViewCallback);

    if (startNextReport) {
        ensureReportMetadataLocked(type, endTimestamp);
    }

    return report;
}

void Sqlite3StorageBackend::removeReportData(const std::string &name) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    _db << "DELETE FROM report_metric WHERE type = ?" << name;
    _db << "DELETE FROM metric_metadata WHERE reportType = ?" << name;
    _db << "DELETE FROM report WHERE type = ?" << name;
}

void Sqlite3StorageBackend::storeMetricValue(uint8_t version,
                                             const std::string &type,
                                             uint64_t timestamp,
                                             const std::string &eventName,
                                             bool internal,
                                             uint64_t aggregationTypes,
                                             const std::string &value,
                                             MetricValueType valueType,
                                             const std::string &dataType,
                                             const std::string &metricType,
                                             bool carryOver) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);

    ensureReportMetadataLocked(type, timestamp);

    _metricUpsertMetadataStmt.reset();
    _metricUpsertMetadataStmt << type << eventName << metricType
        << dataType << carryOver << aggregationTypes << (internal ? 1 : 0)
        << valueType;
    _metricUpsertMetadataStmt.execute();

    _metricInsertStmt.reset();
    _metricInsertStmt << eventName << type << version << timestamp << value;
    _metricInsertStmt.execute();
}

void Sqlite3StorageBackend::ensureReportMetadataLocked(const std::string &type, uint64_t timestamp) {
    _reportInsertMetadata.reset();
    _reportInsertMetadata << type << timestamp;
    _reportInsertMetadata.execute();
}

std::unique_ptr<Report>
Sqlite3StorageBackend::collectMetricsLocked(uint8_t version, const std::string &type, uint64_t startTimestamp,
                                            uint64_t endTimestamp, bool includeHdMetrics,
                                            std::function<void(const MetricDetailedView&)> metricDetailedViewCallback) {
    auto report = std::make_unique<Report>(version, type, startTimestamp, endTimestamp);

    // It's just quicker to calculate the basic set for all metrics
    _db << "SELECT "
           "MIN(CAST(value as DOUBLE)) as min,"
           "MAX(CAST(value as DOUBLE)) as max,"
           "SUM(value) as sum,"
           "AVG(value) as mean,"
           "COUNT(value) as count,"
           "aggregations as aggregationType,"
           "r.eventName as eventName, "
           "valueType, "
           "internal "
           "FROM report_metric r, metric_metadata m "
           "WHERE type = ? "
           "  AND r.eventName = m.eventName "
           "  AND r.type = m.reportType "
           "GROUP BY type, r.eventName "
           "ORDER BY timestamp ASC" << type >> [&](double min, double max, double sum, double mean, double count,
                   uint64_t aggregationTypes, const std::string &eventName, int valueTypeAsInt, int internal) {
        auto valueType = static_cast<MetricValueType>(valueTypeAsInt);

        if (aggregationTypes & NUMERIC_MIN) {
            report->addMetric(eventName + MIN_SUFFIX, internal, asString(min), valueType);
        }
        if (aggregationTypes & NUMERIC_MAX) {
            report->addMetric(eventName + MAX_SUFFIX, internal, asString(max), valueType);
        }
        if (aggregationTypes & NUMERIC_SUM) {
            report->addMetric(eventName + SUM_SUFFIX, internal, asString(sum), valueType);
        }
        if (aggregationTypes & NUMERIC_MEAN) {
            report->addMetric(eventName + MEAN_SUFFIX, internal, asString(mean), Double);
        }
        if (aggregationTypes & NUMERIC_COUNT) {
            report->addMetric(eventName + COUNT_SUFFIX, internal, asString(count), valueType);
        }
        if (aggregationTypes & STATE_LATEST_VALUE) {
            std::string latestValue;
            _metricLastValueStmt.reset();
            _metricLastValueStmt << eventName << type >> latestValue;
            report->addMetric(eventName + LATEST_VALUE_SUFFIX, internal, latestValue, valueType);
        }
        if (aggregationTypes & (STATE_TIME_TOTALS | STATE_TIME_PER_HOUR)) {
            // Note: this could likely be achieved with sqlite window functions but these are only
            // available from sqlite 3.28.0 onwards, which is available in Android 11+.

            std::unordered_map<std::string, uint64_t> timeSpentInState;
            uint64_t currentTime = startTimestamp;
            std::string lastState;
            bool isInitialStateSwitch = true;

            _metricSelectAllStmt.reset();
            _metricSelectAllStmt << eventName << type >> [&](uint64_t timestamp, const std::string &value) {
                uint64_t timeDifference = timestamp - currentTime;

                if (isInitialStateSwitch) isInitialStateSwitch = false;
                else timeSpentInState.emplace(lastState, 0).first->second += timeDifference;

                lastState = value;
                currentTime = timestamp;
            };

            uint64_t timeDifference = endTimestamp - currentTime;
            timeSpentInState.emplace(lastState, 0).first->second += timeDifference;

            for (auto &it : timeSpentInState) {
                if (aggregationTypes & STATE_TIME_TOTALS) {
                    report->addMetric(eventName + "_" + it.first + TIME_TOTALS_SECS,
                                      internal,
                                      asString(it.second / 1000.0), Uint64);
                }
                if (aggregationTypes & STATE_TIME_PER_HOUR) {
                    double hoursInReport = std::max(1.0, (endTimestamp - startTimestamp) / 1000.0 / 60 / 60);
                    report->addMetric(eventName + "_" + it.first + TIME_PER_HOUR_SECS_HOUR,
                                      internal,
                                      asString(it.second / 1000.0 / hoursInReport), Double);
                }
            }
        }
    };

    if (includeHdMetrics) {
        _db << "SELECT "
               " eventName,"
               " metricType,"
               " dataType,"
               " internal "
               " FROM metric_metadata "
               " WHERE reportType = ?" << type >> [&](const std::string &eventName, const std::string &metricType,
                   const std::string &dataType, int internal) {
            const MetricMetadata metadata = { eventName, type, metricType, dataType, internal == 1 };
            Sqlite3MetricDetailedView view(*this, metadata);
            metricDetailedViewCallback(view);
        };
    }

    // Before deleting report data, backup metrics that have carryOver = 1
    _db << "DROP TABLE IF EXISTS temp.metric_metadata_carryovers";
    _db << "DROP TABLE IF EXISTS temp.report_metric_carryovers";
    _db << "CREATE TABLE temp.metric_metadata_carryovers AS"
           " SELECT * FROM metric_metadata WHERE reportType = ? AND carryOver = 1"
           << type;
    _db << "CREATE TABLE temp.report_metric_carryovers AS "
           "SELECT "
           " metric.* "
           "FROM metric_metadata meta, report_metric metric "
           "LEFT JOIN report_metric metric2 ON (metric.eventName = metric2.eventName AND metric.type = metric2.type AND metric.rowid < metric2.rowid) "
           "WHERE meta.eventName = metric.eventName "
           "  AND metric2.rowid IS NULL "
           "  AND reportType = ? AND carryOver = 1" << type;

    removeReportData(type);

    // restore carryover metrics
    _db << "SELECT COUNT(rowId) FROM temp.metric_metadata_carryovers" >> [&](int count) {
        if (count > 0) {
            _reportInsertMetadata.reset();
            _reportInsertMetadata << type << endTimestamp;
            _reportInsertMetadata.execute();
        }
    };
    _db << "UPDATE temp.report_metric_carryovers set timestamp = ?" << endTimestamp;
    _db << "INSERT INTO metric_metadata SELECT * FROM temp.metric_metadata_carryovers";
    _db << "INSERT INTO report_metric SELECT * FROM temp.report_metric_carryovers";
    _db << "DROP TABLE IF EXISTS temp.metric_metadata_carryovers";
    _db << "DROP TABLE IF EXISTS temp.report_metric_carryovers";

    return report;
}

void Sqlite3MetricDetailedView::forEachDatum(std::function<void(const MetricDatum &)> callback) const {
    storage._db << "SELECT "
                   "   timestamp, "
                   "   CAST(value as decimal) as doubleValue, "
                   "   CAST(value as INT) as booleanValue, "
                   "   value as stringValue "
                   "FROM report_metric "
                   "WHERE eventName = ? "
                   "  AND type = ? "
                   "ORDER BY rowid" << _metadata.eventName << _metadata.reportType >> [&](int64_t timestamp, double doubleValue, int booleanValue,
                       const std::string &stringValue) {
        MetricDatum datum = {
            .timestamp = timestamp,
            .strValue = stringValue,
            .numberValue = doubleValue,
            .booleanValue = booleanValue == 1
        };
        callback(datum);
    };
}

void Sqlite3BootIdDumpView::forEachEvent(std::function<void(LogEntry &)> callback) {
    storage._db << "SELECT timestamp, type, blob, internal FROM log WHERE bootRowId = ? ORDER BY timestamp ASC"
        << bootRowId
        >> [&](int64_t timestamp, std::string type, std::string blob, bool internal) {
            auto entry = LogEntry(timestamp, std::move(type), std::move(blob), internal);
            callback(entry);
        };
}

void Sqlite3BootIdDumpView::consumeCid() {
    storage.consumeCid();
}

std::pair<std::string, std::string> Sqlite3BootIdDumpView::getCidPair() {
    return storage.getCidPair();
}

}
