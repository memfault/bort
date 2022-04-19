#define LOG_TAG "DUMPING"
#include <unistd.h>
#include <thread>

#include "dumper.h"
#include "logwriter.h"
#include "log.h"

namespace structured {

void Dumper::run() {
    while (!terminated) {
        const bool skipLatest = dumpOldEntriesOnBoot;

        if (dumpImmediately) dumpImmediately = false;
        else if (dumpOldEntriesOnBoot) {
            while (!isReadyForDump() && !terminated) {
                ALOGV("Not yet ready for dumping, waiting");
                std::this_thread::sleep_for(std::chrono::seconds(5));
            }
            dumpOldEntriesOnBoot = false;
        } else {
            std::unique_lock<std::mutex> lock(dumpMutex);
            auto start = std::chrono::steady_clock::now();
            dumpCond.wait_for(lock, std::chrono::milliseconds (dumpPeriod - elapsedTimeAdjustment));
            uint64_t elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now() - start)
                    .count();
            elapsedTimeAdjustment = 0;

            if (terminated) {
                break;
            }
            if (changingDumpPeriod) {
                dumpPeriod = newDumpPeriod;
                changingDumpPeriod = false;

                // If the new dump period is larger than the already elapsed time, continue and subtract the already
                // elapsed time from the next cycle. Otherwise, dump and restart a new cycle
                if (newDumpPeriod > elapsedMs) {
                    elapsedTimeAdjustment = newDumpPeriod - elapsedMs;
                    continue;
                }
            }
        }

        if (!isReadyForDump()) continue;
        if (backend->getAvailableSpace() < config->getMinStorageThreshold()) {
            ALOGE("Dumping of structured logs aborted because available storage is lower than the configured threshold");
            continue;
        }
        backend->dump(skipLatest, [&](BootIdDumpView &dumpView) {
            auto cids = dumpView.getCidPair();
            int eventCount = 0;
            {
                std::ofstream output(dumpFile);
                JsonLogWriter writer(output, dumpView.getBootId(), cids.first, cids.second);
                dumpView.forEachEvent([&writer, &eventCount](structured::LogEntry &entry) {
                    writer.write(entry);
                    eventCount++;
                });
            }
            if (eventCount > 0 && handleDump(eventCount, dumpFile)) {
                dumpView.consumeCid();
            }
            unlink(dumpFile.c_str());
        });
    }
}

void Dumper::triggerDump() {
    dumpCond.notify_all();
}

void Dumper::changeDumpPeriod(uint64_t newDumpPeriod) {
    if (newDumpPeriod != dumpPeriod) {
        changingDumpPeriod = true;
        this->newDumpPeriod = newDumpPeriod;
        dumpCond.notify_all();
    }
}

void Dumper::terminate() {
    terminated = true;
    dumpCond.notify_all();
}

}
