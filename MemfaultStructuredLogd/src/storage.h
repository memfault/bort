#pragma once

#include <exception>
#include <functional>
#include <iostream>
#include <mutex>
#include <string>
#include <sstream>

#include <cstdint>
#include <utility>

#include <sqlite3.h>
#include <memory>

#include <sqlite_modern_cpp.h>
#include "metrics.h"

namespace structured {

class LogEntry {
  public:
    LogEntry(
        int64_t timestamp,
        std::string type,
        std::string blob,
        bool internal = false)
        : timestamp(timestamp), type(std::move(type)), blob(std::move(blob)), internal(internal) {}

    int64_t timestamp;
    std::string type;
    std::string blob;
    bool internal;
};

class BootIdDumpView {
public:
    explicit BootIdDumpView(
            const std::string &bootId
            ) : bootId(bootId) {};
    virtual ~BootIdDumpView() = default;
    const std::string& getBootId() { return bootId; };
    virtual std::pair<std::string, std::string> getCidPair() = 0;
    virtual void forEachEvent(std::function<void(LogEntry&)> callback) = 0;
    virtual void consumeCid() = 0;
protected:
    const std::string &bootId;
};

typedef std::function<void(void)> OnStorageEmptyListener;
class StorageBackend {
  public:
    virtual void store(const LogEntry &entry) = 0;
    virtual void dump(bool skipLatest, std::function<void(BootIdDumpView&)> callback) = 0;
    virtual std::string getConfig() = 0;
    virtual void setConfig(const std::string &config) = 0;
    virtual ~StorageBackend() = default;
    virtual void addStorageEmtpyListener(OnStorageEmptyListener listener) = 0;
    virtual uint64_t getAvailableSpace() = 0;

    virtual std::unique_ptr<Report> finishReport(uint8_t version, const std::string &name, int64_t endTimestamp,
                                                 bool startNextReport) = 0;
    virtual void storeMetricValue(
            uint8_t version,
            const std::string &type,
            uint64_t timestamp,
            const std::string &eventName,
            bool internal,
            uint64_t aggregationTypes,
            const std::string &value,
            MetricValueType valueType
    ) = 0;

    typedef std::shared_ptr<StorageBackend> SharedPtr;
};

class Sqlite3Exception: public std::exception {
public:
    explicit Sqlite3Exception(const char* msg, int error) {
        std::stringstream what;
        what << msg << ": " << error;
        this->msg = what.str();
    }
    const char* what() const noexcept override {
        return msg.c_str();
    }
private:
    std::string msg;
};

/**
 * Extends the database so that we can initialize and migrate as a single step.
 */
class SqliteDatabase : public sqlite::database {
public:
    explicit SqliteDatabase(const std::string &path);
private:
    void migrate();
    int getDbVersion();
    void setDbVersion(int version);
};

class Sqlite3StorageBackend;
class Sqlite3BootIdDumpView: public BootIdDumpView {
public:
    explicit Sqlite3BootIdDumpView(Sqlite3StorageBackend &storage,
                                   const std::string& bootId,
                                   int64_t bootRowId)
            : BootIdDumpView(bootId),
              storage(storage),
              bootRowId(bootRowId) {}

    void forEachEvent(std::function<void (LogEntry &)> callback) override;
    void consumeCid() override;
    std::pair<std::string, std::string> getCidPair() override;
private:
    Sqlite3StorageBackend &storage;
    int64_t bootRowId;
};

// Return a fixed amount for in-memory databases used in testing
constexpr uint64_t kInMemoryAvailableSpace = 2u * 1024 * 1024 * 1024;
class Sqlite3StorageBackend: public StorageBackend {
  public:
    explicit Sqlite3StorageBackend(
            const std::string &path,
            const std::string &bootId);
    ~Sqlite3StorageBackend() noexcept override {}

    void store(const LogEntry &entry) override;
    void dump(bool skipLatest, std::function<void(BootIdDumpView&)> callback) override;
    void addStorageEmtpyListener(OnStorageEmptyListener listener) override;
    std::string getConfig() override;
    void setConfig(const std::string &config) override;
    uint64_t getAvailableSpace() override;

    std::unique_ptr<Report> finishReport(uint8_t version, const std::string &name, int64_t endTimestamp,
                                         bool startNextReport) override;
    void storeMetricValue(
            uint8_t version,
            const std::string &type,
            uint64_t timestamp,
            const std::string &eventName,
            bool internal,
            uint64_t aggregationTypes,
            const std::string &value,
            MetricValueType valueType
    ) override;

    inline int getBootIdRow() const { return bootIdRow; }
  private:
    void registerBoot(const std::string &bootId);

    SqliteDatabase _db;
    sqlite::database_binder _insertStmt;
    sqlite::database_binder _metricLastValueStmt;
    sqlite::database_binder _metricInsertStmt;
    sqlite::database_binder _metricSelectAllStmt;
    sqlite::database_binder _reportInsertMetadata;
    std::string _path;
    bool _inMemory;
    int64_t bootIdRow;
    friend class Sqlite3BootIdDumpView;
    std::recursive_mutex dbMutex;
    std::string storageDir;

    std::string generateCid();
    void ensureCids();
    void consumeCid();
    void ensureConfig();
    std::pair<std::string, std::string> getCidPair();
    std::vector<OnStorageEmptyListener> storageEmptyListeners;

    std::unique_ptr<Report> collectMetricsLocked(uint8_t version, const std::string &type, uint64_t startTimestamp, uint64_t endTimestamp);

    void ensureReportMetadataLocked(const std::string &type, uint64_t timestamp);
    void removeReportData(const std::string &name);
};

}
