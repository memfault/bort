#pragma once

#include <atomic>
#include <memory>
#include <string>
#include "storage.h"
#include "dumper.h"
#include "config.h"
#include "rate_limiter.h"

namespace structured {
class Logger {
public:
    Logger(
            std::shared_ptr<StorageBackend> &storage,
            std::shared_ptr<Dumper> &dumper,
            std::shared_ptr<Config> &config,
            std::unique_ptr<TokenBucketRateLimiter> &rateLimiter
    )  : storage(storage), dumper(dumper), config(config), counter(0u), rateLimiter(std::move(rateLimiter)) {
    }

    void log(
            int64_t timestamp,
            const std::string &type,
            const std::string &data,
            bool internal = false
    );

    void triggerDump();

    void reloadConfig(const std::string &configJson);

private:
    std::shared_ptr<StorageBackend> storage;
    std::shared_ptr<Dumper> dumper;
    std::shared_ptr<Config> config;
    std::atomic<uint32_t> counter;
    std::unique_ptr<TokenBucketRateLimiter> rateLimiter;
};
}  // namespace structured
