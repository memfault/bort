LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := structured-client-example-c
LOCAL_SRC_FILES := main.c
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libmflt-structuredlog
include $(BUILD_EXECUTABLE)
