LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libmflt-structuredlog
LOCAL_SRC_FILES := structuredlog.cpp
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libstructuredaidl libbase libbinder libutils libservices
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)
include $(BUILD_SHARED_LIBRARY)
