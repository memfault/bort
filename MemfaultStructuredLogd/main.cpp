#define LOG_NDEBUG 0
#define LOG_TAG "structured"

#include "src/android.h"
#include <log/log.h>

#define DB_STORAGE_PATH "/data/system/MemfaultStructuredLogd/log.db"

int main(int argc __unused, char** argv __unused) {
  int result = 0;

  try {
      structured::createService(DB_STORAGE_PATH);
  } catch (std::exception& e) {
    ALOGE("Initialization failure: %s", e.what());
    result = -1;
  }
  return result;
}
