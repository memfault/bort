LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := reporting-client-example-cpp
LOCAL_SRC_FILES := main.cpp
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libmflt-reporting
include $(BUILD_EXECUTABLE)
