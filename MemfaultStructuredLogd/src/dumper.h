#pragma once

#include <functional>
#include <mutex>
#include <string>
#include <condition_variable>
#include <utility>

#include "storage.h"
#include "config.h"

namespace structured {

class Dumper {
public:
    Dumper(
            std::string dumpFile,
            std::shared_ptr<Config> &config,
            std::shared_ptr<StorageBackend> &backend,
            std::function<bool(int, std::string)> handleDump,
            std::function<bool()> isReadyForDump,
            uint64_t dumpPeriod,
            bool dumpOldEntriesOnBoot = true,
            bool dumpImmediately = false /* for testing */
    ) : dumpFile(std::move(dumpFile)), config(config), backend(backend), handleDump(std::move(handleDump)),
        isReadyForDump(std::move(isReadyForDump)), terminated(false), dumpPeriod(dumpPeriod),
        dumpOldEntriesOnBoot(dumpOldEntriesOnBoot), dumpImmediately(dumpImmediately), changingDumpPeriod(false),
        elapsedTimeAdjustment(0) {}
    virtual ~Dumper() {}

    void run();
    virtual void triggerDump();
    void changeDumpPeriod(uint64_t newDumpPeriod);
    void terminate();

    typedef std::shared_ptr<Dumper> SharedPtr;
private:
    const std::string dumpFile;
    std::shared_ptr<Config> config;
    std::shared_ptr<StorageBackend> backend;
    const std::function<bool(int, std::string)> handleDump;
    const std::function<bool()> isReadyForDump;
    bool terminated;

    std::mutex dumpMutex;
    std::condition_variable dumpCond;
    uint64_t dumpPeriod;
    bool dumpOldEntriesOnBoot;
    bool dumpImmediately;
    bool changingDumpPeriod;
    uint64_t newDumpPeriod;
    uint64_t elapsedTimeAdjustment;
};

}  // namespace structured
