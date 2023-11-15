#include "rate_limiter.h"
#include <gtest/gtest.h>

using namespace structured;

namespace {

TEST(RateLimiterTest, TestFeed) {
    uint64_t now = 0;
    const RateLimiterConfig config = RateLimiterConfig{
            .initialCapacity = 0,
            .capacity = 3,
            .msPerToken = 2
    };
    TokenBucketRateLimiter bucket(
            config,
            [&now]() { return now; }
    );

    now = 1;
    bucket.feed();
    ASSERT_EQ(0u, bucket.getTokens());

    now = 2;
    bucket.feed();
    ASSERT_EQ(1u, bucket.getTokens());

    now = 10;
    bucket.feed();
    ASSERT_EQ(3u, bucket.getTokens());
}

TEST(RateLimiterTest, TestTake) {
    uint64_t now = 0;
    const RateLimiterConfig config = {
            .initialCapacity = 3,
            .capacity = 3,
            .msPerToken = 2
    };
    TokenBucketRateLimiter bucket(
            config,
            [&now]() { return now; }
    );

    ASSERT_FALSE(bucket.take(4));
    ASSERT_EQ(3u, bucket.getTokens());
    ASSERT_TRUE(bucket.take(2));
    ASSERT_EQ(1u, bucket.getTokens());
    ASSERT_TRUE(bucket.take(1));
    ASSERT_EQ(0u, bucket.getTokens());
    ASSERT_FALSE(bucket.take(1));
    ASSERT_EQ(0u, bucket.getTokens());
}

TEST(RateLimiterTest, TestTakeWithLongPeriod) {
    uint64_t now = 0;
    const RateLimiterConfig config = {
            .initialCapacity = 0,
            .capacity = 1,
            .msPerToken = 1
    };
    TokenBucketRateLimiter bucket(config, [&now]() { return now; }
    );

    now = 5;
    ASSERT_TRUE(bucket.take(1));
    ASSERT_FALSE(bucket.take(1));
}

TEST(RateLimiterTest, TestFeedRate) {
    uint64_t now = 0;
    const RateLimiterConfig config = {
            .initialCapacity = 0,
            .capacity = 10,
            .msPerToken = 1
    };
    TokenBucketRateLimiter bucket(config, [&now]() { return now; });

    now = 10;
    bucket.feed();
    ASSERT_EQ(10u, bucket.getTokens());

    bucket.take(10);
    ASSERT_EQ(0u, bucket.getTokens());

    for (now = 11; now <= 20; now++) {
        bucket.feed();
        ASSERT_EQ(now-10, bucket.getTokens());
    }

}

TEST(RateLimiterTest, TestReconfigure) {
    const RateLimiterConfig config = {
            .initialCapacity = 0,
            .capacity = 10,
            .msPerToken = 1
    };
    uint64_t now = 0;
    TokenBucketRateLimiter bucket(config, [&now]() { return now; });

    now = 10;
    bucket.feed();
    ASSERT_EQ(10u, bucket.getTokens());

    const RateLimiterConfig newConfig = {
            .initialCapacity = 0,
            .capacity = 4,
            .msPerToken = 2
    };
    bucket.reconfigure(newConfig);

    // Tokens are trimmed to the new capacity
    ASSERT_EQ(4u, bucket.getTokens());
    bucket.take(4);

    now += 4;
    bucket.feed();
    // new period is applied
    ASSERT_EQ(2u, bucket.getTokens());
}

}  // namespace
