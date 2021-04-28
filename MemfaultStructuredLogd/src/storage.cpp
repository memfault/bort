#include "storage.h"

#include <fstream>
#include <memory>
#include <utility>
#include "rate_limiter.h"
#include "log.h"

namespace structured {

enum versions {
  INITIAL = 0,
  LOG_TABLE,
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
        const std::string &bootId,
        TokenBucketRateLimiter &rateLimiter
)
        : _db(path),
          _insertStmt(_db << "INSERT INTO log (timestamp, type, blob, bootRowId, internal) VALUES(?, ?, ?, ?, ?)"),
          rateLimiter(rateLimiter) {
    registerBoot(bootId);
    ensureCids();
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
    store(entry, DEFAULT_COST);
}

void Sqlite3StorageBackend::store(const LogEntry &entry, uint32_t cost) {
  if (!rateLimiter.take(cost)) {
      ALOGV("Entry ignored by rate limiter");
      return;
  }
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
