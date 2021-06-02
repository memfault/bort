#pragma once

#ifndef __ANDROID__
#include <cstdio>
#define ALOGV(...) fprintf(stderr, __VA_ARGS__)
#define ALOGD(...) fprintf(stderr, __VA_ARGS__)
#define ALOGI(...) fprintf(stderr, __VA_ARGS__)
#define ALOGE(...) fprintf(stderr, __VA_ARGS__)
#define ALOGT(...) fprintf(stderr, __VA_ARGS__)
#define ALOGW(...) fprintf(stderr, __VA_ARGS__)
#else
#ifndef LOG_TAG
#define LOG_TAG "structured"
#endif
#include <log/log.h>
//! Log which E2E tests depend upon:
#define ALOGT(...) ((void)ALOG(LOG_VERBOSE, "structured-test", __VA_ARGS__))
#endif
