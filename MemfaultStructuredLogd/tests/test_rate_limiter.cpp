#include "rate_limiter.h"
#include <gtest/gtest.h>

using namespace structured;

namespace {

TEST(RateLimiterTest, TestFeed) {
    uint64_t now = 0;
    TokenBucketRateLimiter bucket(
            2,
            3,
            0,
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
    TokenBucketRateLimiter bucket(
            2,
            3,
            3,
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
    TokenBucketRateLimiter bucket(
            1,
            1,
            0,
            [&now]() { return now; }
    );

    now = 5;
    ASSERT_TRUE(bucket.take(1));
    ASSERT_FALSE(bucket.take(1));
}

TEST(RateLimiterTest, TestFeedRate) {
    uint64_t now = 0;
    TokenBucketRateLimiter bucket(
            1,
            10,
            0,
            [&now]() { return now; }
    );

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

}