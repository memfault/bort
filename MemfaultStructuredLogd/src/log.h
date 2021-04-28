#pragma once

#ifndef __ANDROID__
#include <cstdio>
#define ALOGV(...) fprintf(stderr, __VA_ARGS__)
#define ALOGD(...) fprintf(stderr, __VA_ARGS__)
#define ALOGI(...) fprintf(stderr, __VA_ARGS__)
#define ALOGE(...) fprintf(stderr, __VA_ARGS__)
#else
#ifndef LOG_TAG
#define LOG_TAG "structured"
#endif
#include <log/log.h>
#endif