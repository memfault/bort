#ifdef __ANDROID__
#include <android-base/logging.h>
#endif
#include <gtest/gtest.h>

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
#ifdef __ANDROID__
  android::base::InitLogging(argv, android::base::StderrLogger);
#endif
  return RUN_ALL_TESTS();
}
