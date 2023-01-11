#include <atomic>
#include <fstream>
#include <memory>
#include <streambuf>
#include <memory>

#include <gtest/gtest.h>

#include <logwriter.h>
#include <storage.h>
#include <dumper.h>
#include <thread>
#include <rapidjson/document.h>

using namespace structured;

#ifdef __ANDROID__
#define TMP_PATH_PREFIX "/data/local/tmp/"
#else
#define TMP_PATH_PREFIX
#endif

namespace {

TEST(DumperTest, DumpingGetsCalledForEachBootId) {
    char tmpDb[] = {TMP_PATH_PREFIX "dumper-db-XXXXXX"};
    mkstemp(tmpDb);
    char tmpFile[] = {TMP_PATH_PREFIX "dumper-test-XXXXXX"};
    mkstemp(tmpFile);
    {
        Sqlite3StorageBackend backend(tmpDb, "boot-1");
        backend.store(LogEntry(1234567, "dummy", R"j([1,2,3])j"));
    }
    {
        auto backend = std::shared_ptr<StorageBackend>(new Sqlite3StorageBackend(tmpDb, "boot-2"));
        auto config = std::shared_ptr<Config>(new StoredConfig(backend));
        backend->store(LogEntry(1234567, "dummy", R"j([4,5,6])j"));
        std::atomic_int callCounter(0);
        std::condition_variable done;
        auto dumper = std::make_shared<Dumper>(tmpFile, config, backend, [&](int nEvents, const std::string &dumpPath) -> bool {
            std::ifstream ifs(dumpPath);
            std::string jsonOutput((std::istreambuf_iterator<char>(ifs)), std::istreambuf_iterator<char>());
            rapidjson::Document document;
            document.Parse(jsonOutput.c_str());

            // ASSERT_* expect a void return type but this lambda returns bool
            // See https://groups.google.com/a/chromium.org/g/chromium-dev/c/7clymoTb3A0
            [&]() {
                ASSERT_FALSE(document.HasParseError());
                ASSERT_EQ(document["schema_version"].GetInt(), 1);
                ASSERT_EQ(document["events"].GetArray().Size(), 1u);
                ASSERT_EQ(document["events"].GetArray()[0]["data"].GetArray().Size(), 3u);

                switch (callCounter++) {
                    case 0:
                        ASSERT_EQ(document["linux_boot_id"], "boot-1");
                        break;
                    case 1:
                        ASSERT_EQ(document["linux_boot_id"], "boot-2");
                        done.notify_all();
                        break;
                }
            }();
            return true;
        }, []() { return true; }, 10000, false, true);

        std::thread dumperThread(&Dumper::run, dumper);

        std::mutex waitMutex;
        std::unique_lock<std::mutex> lock(waitMutex);
        done.wait_for(lock, std::chrono::seconds(10));

        dumper->terminate();
        dumperThread.join();

        ASSERT_EQ(callCounter, 2);
    }

    unlink(tmpDb);
    unlink(tmpFile);
}

TEST(DumperTest, DumpingGetsNumberOfEvents) {
    char tmpDb[] = {TMP_PATH_PREFIX "dumper-db-XXXXXX"};
    mkstemp(tmpDb);
    char tmpFile[] = {TMP_PATH_PREFIX "dumper-test-XXXXXX"};
    mkstemp(tmpFile);

    {
        auto backend = std::shared_ptr<StorageBackend>(new Sqlite3StorageBackend(tmpDb, "boot-1"));
        auto config = std::shared_ptr<Config>(new StoredConfig(backend));
        backend->store(LogEntry(1234567, "dummy", R"j([4,5,6])j"));
        backend->store(LogEntry(1234568, "dummy2", R"j([4,5,6])j"));
        std::condition_variable done;
        auto dumper = std::make_shared<Dumper>(tmpFile, config, backend, [&](int nEvents, const std::string &dumpPath) -> bool {
            [&]() {
                ASSERT_EQ(2, nEvents);
            }();
            done.notify_all();
            return true;
        }, []() { return true; }, 10000, false, true);

        std::thread dumperThread(&Dumper::run, dumper);
        std::mutex waitMutex;
        std::unique_lock<std::mutex> lock(waitMutex);
        done.wait_for(lock, std::chrono::seconds(10));

        dumper->terminate();
        dumperThread.join();
    }

    unlink(tmpDb);
    unlink(tmpFile);
}

}
