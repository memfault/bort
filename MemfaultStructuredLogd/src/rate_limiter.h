#pragma once

#include <algorithm>
#include <functional>

namespace structured {

class RateLimiter {
public:
    virtual ~RateLimiter() = default;
    virtual bool take(uint32_t amount = 1) = 0;
};

class TokenBucketRateLimiter : public RateLimiter {
public:
    explicit TokenBucketRateLimiter(
            uint64_t msPerToken,
            uint32_t capacity,
            uint32_t initialCapacity,
            const std::function<uint64_t(void)>& getElapsedMs
    ) : msPerToken(msPerToken), capacity(capacity), tokens(initialCapacity), getElapsedMs(getElapsedMs) {
        lastFeedTime = getElapsedMs();
    }

    bool take(uint32_t amount) override {
        feed();
        if (tokens < amount) return false;
        else {
            tokens -= amount;
            return true;
        }
    }

    void feed() {
        const uint64_t now = getElapsedMs();
        const double periods = double(now - lastFeedTime) / msPerToken;
        if (periods < 1.0) {
            return;
        }
        tokens = std::min(tokens + int(periods), capacity);
        lastFeedTime += (periods * msPerToken);
    }

    inline uint32_t getTokens() const { return tokens; }
private:
    uint64_t msPerToken;
    uint32_t capacity;
    uint32_t tokens;
    const std::function<int64_t(void)> getElapsedMs;
    uint64_t lastFeedTime;
};

}