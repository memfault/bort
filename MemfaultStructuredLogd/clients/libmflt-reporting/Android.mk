LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libmflt-reporting
LOCAL_SRC_FILES := reporting.cpp
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions -Wno-unused-private-field -DIN_LIB_DEV
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libmflt-structuredlog liblog
LOCAL_C_INCLUDES := $(LOCAL_PATH)/../../deps/rapidjson-1.1.0/include
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH) $(LOCAL_PATH)/../libmflt-structuredlog/
include $(BUILD_SHARED_LIBRARY)
