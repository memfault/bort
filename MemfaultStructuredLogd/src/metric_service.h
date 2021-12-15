#pragma once

#include <string>
#include <utility>
#include "metric_reporter.h"

namespace structured {

class MetricService {
public:
    explicit MetricService(
            std::shared_ptr<StoredReporter> reporter,
            std::shared_ptr<Config> &config
    ) : reporter(std::move(reporter)), config(std::move(config)) {}

    void finishReport(const std::string &json);
    void addValue(const std::string &json);

private:
    std::shared_ptr<StoredReporter> reporter;
    std::shared_ptr<Config> config;
};

}
