#pragma once

#include <functional>
#include <mutex>
#include <string>
#include <condition_variable>
#include <utility>

#include "storage.h"

namespace structured {

class Dumper {
public:
    Dumper(
            std::string dumpFile,
            std::shared_ptr<StorageBackend> backend,
            std::function<bool(int, std::string)> handleDump,
            std::function<bool()> isReadyForDump,
            bool dumpOldEntriesOnBoot = true,
            bool dumpImmediately = false /* for testing */
    ) : dumpFile(std::move(dumpFile)), backend(backend), handleDump(std::move(handleDump)),
        isReadyForDump(std::move(isReadyForDump)), terminated(false),
        dumpOldEntriesOnBoot(dumpOldEntriesOnBoot), dumpImmediately(dumpImmediately) {}

    void run();
    void triggerDump();
    void terminate();
private:
    const std::string dumpFile;
    std::shared_ptr<StorageBackend> backend;
    const std::function<bool(int, std::string)> handleDump;
    const std::function<bool()> isReadyForDump;
    bool terminated;

    std::mutex dumpMutex;
    std::condition_variable dumpCond;
    bool dumpOldEntriesOnBoot;
    bool dumpImmediately;
};

}