#pragma once

#include <algorithm>
#include <functional>
#include "config.h"

namespace structured {

class RateLimiter {
public:
    virtual ~RateLimiter() = default;
    virtual bool take(uint32_t amount = 1) = 0;
};

class TokenBucketRateLimiter : public RateLimiter {
public:
    explicit TokenBucketRateLimiter(const RateLimiterConfig &config,
                                    const std::function<uint64_t(void)>& getElapsedMs
    ) : msPerToken(config.msPerToken), capacity(config.capacity), tokens(config.initialCapacity),
                getElapsedMs(getElapsedMs) {
        lastFeedTime = getElapsedMs();
    }

    bool take(uint32_t amount) override {
        feed();
        std::unique_lock<std::mutex> lock(mutex);
        if (tokens < amount) return false;
        else {
            tokens -= amount;
            return true;
        }
    }

    void feed() {
        std::unique_lock<std::mutex> lock(mutex);
        const uint64_t now = getElapsedMs();
        const double periods = double(now - lastFeedTime) / msPerToken;
        if (periods < 1.0) {
            return;
        }
        tokens = std::min(tokens + int(periods), capacity);
        lastFeedTime += (periods * msPerToken);
    }

    void reconfigure(const RateLimiterConfig &config) {
        std::unique_lock<std::mutex> lock(mutex);
        tokens = std::min(config.capacity, tokens);
        capacity = config.capacity;
        msPerToken = config.msPerToken;
    }

    inline uint32_t getTokens() const { return tokens; }
private:
    uint64_t msPerToken;
    uint32_t capacity;
    uint32_t tokens;
    const std::function<int64_t(void)> getElapsedMs;
    uint64_t lastFeedTime;
    std::mutex mutex;
};

}  // namespace structured
