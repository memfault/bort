LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := reporting-client-example-c
LOCAL_SRC_FILES := main.c

LOCAL_CFLAGS := \
    -Wall \
    -Werror \
    -Wextra \
    -Wno-unused-parameter \
    -fexceptions \
    -Wno-unknown-attributes

LOCAL_SHARED_LIBRARIES := libmflt-reporting

include $(BUILD_EXECUTABLE)
