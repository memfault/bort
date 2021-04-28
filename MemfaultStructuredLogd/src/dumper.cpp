#define LOG_TAG "DUMPING"
#include <unistd.h>
#include <thread>

#include "dumper.h"
#include "logwriter.h"
#include "log.h"

namespace structured {

void Dumper::run() {
    while (!terminated) {
        std::unique_lock<std::mutex> lock(dumpMutex);

        if (dumpImmediately) dumpImmediately = false;
        else if (dumpOldEntriesOnBoot) {
            while (!isReadyForDump() && !terminated) {
                ALOGV("Not yet ready for dumping, waiting");
                std::this_thread::sleep_for(std::chrono::seconds(5));
            }
        } else {
            dumpCond.wait_for(lock, std::chrono::hours(2));
            if (terminated) {
                break;
            }
        }

        if (!isReadyForDump()) continue;
        const bool skipLatest = dumpOldEntriesOnBoot;
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
        dumpOldEntriesOnBoot = false;
    }
}

void Dumper::triggerDump() {
    dumpCond.notify_all();
}

void Dumper::terminate() {
    terminated = true;
    dumpCond.notify_all();
}

}
