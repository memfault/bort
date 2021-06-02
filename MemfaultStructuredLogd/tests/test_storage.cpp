#include <gtest/gtest.h>
#include <storage.h>

using namespace structured;

#define SQLITE3_FILE ":memory:"
// #define SQLITE3_FILE "/tmp/test.db"
// #define SQLITE3_FILE "/data/local/tmp/test.db"

namespace {

TEST (Sqlite3StorageBackendTest, CreationAndMigrationWorks) {
  auto backend = new Sqlite3StorageBackend(SQLITE3_FILE, "id");
  delete backend;
}

TEST (Sqlite3StorageBackendTest, TestBootIdGeneration) {
    // we need persistence for this
    char name[] = "test-db-XXXXXX";
    mkstemp(name);

    // A first boot
    {
        Sqlite3StorageBackend boot1(name, "id1");
        ASSERT_EQ(1, boot1.getBootIdRow());
    }

    // A daemon crash/restart -> same boot
    {
        Sqlite3StorageBackend boot1(name, "id1");
        ASSERT_EQ(1, boot1.getBootIdRow());
    }

    // A follow-up boot
    {
        Sqlite3StorageBackend boot1(name, "id2");
        ASSERT_EQ(2, boot1.getBootIdRow());
    }

    unlink(name);
}

struct CollectedDump {
    std::string cid;
    std::string nextCid;
    std::vector<LogEntry> entries;
};

static std::map<std::string, CollectedDump> dump(Sqlite3StorageBackend &backend, bool skipLatest = false) {
    std::map<std::string, CollectedDump> dumpResults;

    backend.dump(skipLatest, [&](BootIdDumpView &dumpView) {
        dumpResults[dumpView.getBootId()] = CollectedDump{
            .cid = dumpView.getCidPair().first,
            .nextCid = dumpView.getCidPair().second,
        };
        dumpView.forEachEvent([&](structured::LogEntry &entry) {
            dumpResults[dumpView.getBootId()].entries.push_back(entry);
        });
        dumpView.consumeCid();
    });
    return dumpResults;
}

TEST (Sqlite3StorageBackendTest, AddingAndDumpingEventsWorks) {
  // we need persistence for this
  char name[] = "test-db-XXXXXX";
  mkstemp(name);

  {
    Sqlite3StorageBackend backend(name, "boot_id1");
    backend.store(LogEntry(120, "type1", "message1"));
    backend.store(LogEntry(121, "type2", "message2"));
    backend.store(LogEntry(119, "type3", "message3"));
  }

  Sqlite3StorageBackend backend(name, "boot_id2");
  backend.store(LogEntry(140, "type4", "message4"));

  auto dumpResults = dump(backend);

  ASSERT_EQ(2u, dumpResults.size());

  CollectedDump &dump1 = dumpResults["boot_id1"];
  std::vector<LogEntry> &entries = dump1.entries;
  ASSERT_EQ(3u, entries.size());
  ASSERT_EQ(entries[0].type, "type3");
  ASSERT_EQ(entries[0].timestamp, 119);
  ASSERT_EQ(entries[0].blob, "message3");

  ASSERT_EQ(entries[1].type, "type1");
  ASSERT_EQ(entries[1].timestamp, 120);
  ASSERT_EQ(entries[1].blob, "message1");

  ASSERT_EQ(entries[2].type, "type2");
  ASSERT_EQ(entries[2].timestamp, 121);
  ASSERT_EQ(entries[2].blob, "message2");

  CollectedDump &dump2 = dumpResults["boot_id2"];
  std::vector<LogEntry> &entries2 = dump2.entries;
  ASSERT_EQ(1u, entries2.size());
  ASSERT_EQ(entries2[0].type, "type4");
  ASSERT_EQ(entries2[0].timestamp, 140);
  ASSERT_EQ(entries2[0].blob, "message4");

  ASSERT_EQ(dump1.nextCid, dump2.cid);
  ASSERT_NE(dump2.nextCid, dump1.nextCid);


  // Check that we deleted everything but kept the boot id
  auto dumpResultsAfterDeletion = dump(backend);
  ASSERT_EQ(1u, dumpResultsAfterDeletion.size());
  ASSERT_TRUE(dumpResultsAfterDeletion.count("boot_id2") != 0);
  ASSERT_TRUE(dumpResultsAfterDeletion["boot_id2"].entries.empty());
}

TEST (Sqlite3StorageBackendTest, SkipLatestWorks) {
    // we need persistence for this
    char name[] = "test-db-XXXXXX";
    mkstemp(name);
    {
        Sqlite3StorageBackend backend(name, "boot_id1");
        backend.store(LogEntry(120, "type1", "message1"));
        backend.store(LogEntry(121, "type2", "message2"));
        backend.store(LogEntry(119, "type3", "message3"));
    }

    Sqlite3StorageBackend backend(name, "boot_id2");
    backend.store(LogEntry(140, "type4", "message4"));

    auto dumpResults = dump(backend, true);
    ASSERT_EQ(1u, dumpResults.size());

    auto dumpWithSkipLatest = dump(backend, true);
    ASSERT_EQ(0u, dumpWithSkipLatest.size());

    auto dumpWithoutSkipLatest = dump(backend, false);
    ASSERT_EQ(1u, dumpWithoutSkipLatest.size());
}

TEST (Sqlite3StorageBackendTest, TestTsOverflow) {
    // Regression test for nanoseconds overflow in storage
    Sqlite3StorageBackend backend(SQLITE3_FILE, "id");
    backend.store(LogEntry(INT64_MAX, "type", "{}"));

    auto dumpResults = dump(backend);
    ASSERT_EQ(1u, dumpResults.size());
    ASSERT_EQ(INT64_MAX, dumpResults["id"].entries[0].timestamp);
}

TEST (Sqlite3StorageBackendTest, TestInternalTypes) {
    Sqlite3StorageBackend backend(SQLITE3_FILE, "id");
    backend.store(LogEntry(INT64_MAX, "type", "{}", true /* internal */));
    backend.store(LogEntry(INT64_MAX, "type", "{}", false /* internal */));

    auto dumpResults = dump(backend);
    ASSERT_EQ(2u, dumpResults["id"].entries.size());
    ASSERT_EQ(true, dumpResults["id"].entries[0].internal);
    ASSERT_EQ(false, dumpResults["id"].entries[1].internal);
}

TEST (Sqlite3StorageBackendTest, TestEmptyListener) {
    Sqlite3StorageBackend backend(SQLITE3_FILE, "id");
    backend.addStorageEmtpyListener([&backend](){
        backend.store(LogEntry(2, "fake.rtc.sync", "{}"));
    });

    backend.store(LogEntry(1, "type", "{}"));
    dump(backend);

    auto afterEmpty = dump(backend);
    ASSERT_EQ(1u, afterEmpty["id"].entries.size());
    ASSERT_EQ("fake.rtc.sync", afterEmpty["id"].entries[0].type);
}

TEST (Sqlite3StorageBackendTest, TestConfig) {
    Sqlite3StorageBackend backend(SQLITE3_FILE, "id");

    auto initialConfig = backend.getConfig();
    ASSERT_EQ("", initialConfig);

    std::string updatedConfig("{\"test: true\"}");
    backend.setConfig(updatedConfig);
    ASSERT_EQ(updatedConfig, backend.getConfig());
}

}
