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
  MAX,
};

void SqliteDatabase::migrate() {
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
          _metricInsertStmt(_db << "INSERT INTO report_metric"
                                   "(eventName, type, internal, version, timestamp, aggregations, value, valueType) "
                                   "VALUES(?, ?, ?, ?, ?, ?, ?, ?)"),
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
        bool startNextReport
) {
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

    auto report = collectMetricsLocked(version, type, startTimestamp, endTimestamp);

    if (startNextReport) {
        ensureReportMetadataLocked(type, endTimestamp);
    }

    return report;
}

void Sqlite3StorageBackend::removeReportData(const std::string &name) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);
    _db << "DELETE FROM report_metric WHERE type = ?" << name;
    _db << "DELETE FROM report WHERE type = ?" << name;
}

void Sqlite3StorageBackend::storeMetricValue(uint8_t version,
                                             const std::string &type,
                                             uint64_t timestamp,
                                             const std::string &eventName,
                                             bool internal,
                                             uint64_t aggregationTypes,
                                             const std::string &value,
                                             MetricValueType valueType) {
    std::unique_lock<std::recursive_mutex> lock(dbMutex);

    ensureReportMetadataLocked(type, timestamp);

    _metricInsertStmt.reset();
    _metricInsertStmt << eventName << type << (internal ? 1 : 0) << version << timestamp << aggregationTypes
            << value << valueType;
    _metricInsertStmt.execute();
}

void Sqlite3StorageBackend::ensureReportMetadataLocked(const std::string &type, uint64_t timestamp) {
    _reportInsertMetadata.reset();
    _reportInsertMetadata << type << timestamp;
    _reportInsertMetadata.execute();
}

std::unique_ptr<Report>
Sqlite3StorageBackend::collectMetricsLocked(uint8_t version, const std::string &type, uint64_t startTimestamp,
                                            uint64_t endTimestamp) {
    auto report = std::make_unique<Report>(version, type, startTimestamp, endTimestamp);

    // It's just quicker to calculate the basic set for all metrics
    _db << "SELECT "
           "MIN(value) as min,"
           "MAX(value) as max,"
           "SUM(value) as sum,"
           "AVG(value) as mean,"
           "COUNT(value) as count,"
           "MAX(aggregations) as aggregationType,"
           "eventName as eventName, "
           "MAX(valueType) as valueType, "
           "MAX(internal) as internal "
           "FROM report_metric "
           "WHERE type = ? "
           "GROUP BY type, eventName "
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
                    report->addMetric(eventName + TIME_TOTALS_SUFFIX + it.first + TIME_TOTALS_SECS,
                                      internal,
                                      asString(it.second / 1000.0), Uint64);
                }
                if (aggregationTypes & STATE_TIME_PER_HOUR) {
                    double hoursInReport = std::max(1.0, (endTimestamp - startTimestamp) / 1000.0 / 60 / 60);
                    report->addMetric(eventName + TIME_PER_HOUR_SUFFIX + it.first + TIME_PER_HOUR_SECS_HOUR,
                                      internal,
                                      asString(it.second / 1000.0 / hoursInReport), Double);
                }
            }
        }
    };

    removeReportData(type);

    return report;
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
