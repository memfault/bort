#pragma once
#include "storage.h"

#include <fstream>
#include <string>
#include <utility>
#include "rapidjson/ostreamwrapper.h"
#include "rapidjson/writer.h"

namespace structured {

class LogWriter {
public:
    virtual ~LogWriter() = default;
    virtual void write(const LogEntry &e)  = 0;
};

class JsonLogWriter: public LogWriter {
public:
    explicit JsonLogWriter(std::ostream &output, const std::string& bootId, const std::string& cid,
                           const std::string &nextCid);
    ~JsonLogWriter() override;
    void write(const LogEntry &e) override;
private:
    rapidjson::OStreamWrapper oStreamWrapper;
    rapidjson::Writer<rapidjson::OStreamWrapper> writer;

    void writeBlob(const LogEntry &entry);
};

}
