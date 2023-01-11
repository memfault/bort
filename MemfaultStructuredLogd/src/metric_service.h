#pragma once

#include <string>
#include <utility>
#include "metric_reporter.h"
#include "rapidjson/document.h"

namespace structured {

class MetricService {
public:
    explicit MetricService(
            std::shared_ptr<StoredReporter> reporter,
            std::shared_ptr<Config> &config
    ) : reporter(std::move(reporter)), config(config) {}

    void finishReport(const std::string &json);
    void addValue(const std::string &json);

private:
    std::shared_ptr<StoredReporter> reporter;
    std::shared_ptr<Config> config;

    void addValueFromObject(const rapidjson::Document::Object &object);
};

}
