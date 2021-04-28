#include <fstream>
#include <streambuf>

#include <gtest/gtest.h>

#include <logwriter.h>
#include <storage.h>

using namespace structured;

#ifdef __ANDROID__
#define TEST_OUTPUT_FILE "/data/local/tmp/json-output"
#else
#define TEST_OUTPUT_FILE "/tmp/json-output"
#endif

namespace {

TEST(JsonLogWriterTest, WritesLogs) {
  std::ostringstream output;
  {
    JsonLogWriter writer(output, "0000-fake-bootid", "now_cid", "next_cid");
    writer.write(LogEntry(123100000, "type1", "{\"extra\": 3}"));
    writer.write(LogEntry(124200000, "type2", "{\"amounts\": [1,2,3]}"));
    writer.write(LogEntry(125300000, "type3", "not valid json, will be ignored and \"escaped\""));
    writer.write(LogEntry(125400000, "type4", "[]", true));
  }

  ASSERT_EQ(output.str(),
            R"j({)j"
            R"j("schema_version":1,)j"
            R"j("linux_boot_id":"0000-fake-bootid",)j"
            R"j("cid":"now_cid",)j"
            R"j("next_cid":"next_cid",)j"
            R"j("events":[)j"
            R"j({"ts":123.1,"type":"type1","data":{"extra": 3}},)j"
            R"j({"ts":124.2,"type":"type2","data":{"amounts": [1,2,3]}},)j"
            R"j({"ts":125.3,"_type":"invalid_data","data":{"original_type":"type3","original_data":"not valid json, will be ignored and \"escaped\""}},)j"
            R"j({"ts":125.4,"_type":"type4","data":[]})j"
            R"j(])j"
            R"j(})j");
}

}
