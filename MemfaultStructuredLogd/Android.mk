LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libstructuredaidl
LOCAL_MODULE_CLASS := SHARED_LIBRARIES

LOCAL_SHARED_LIBRARIES := \
    libbase \
    libbinder \
    libutils

LOCAL_SRC_FILES := \
    com/memfault/bort/internal/ILogger.aidl

aidl_include := $(call local-generated-sources-dir)/aidl-generated/include

LOCAL_C_INCLUDES := $(aidl_include)
LOCAL_EXPORT_C_INCLUDE_DIRS := $(aidl_include)
LOCAL_AIDL_INCLUDES := \
    $(LOCAL_PATH)
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libmflt-reporting

LOCAL_SRC_FILES := \
    clients/libmflt-reporting/reporting.cpp \
    clients/libmflt-reporting/structuredlog.cpp

LOCAL_CPPFLAGS := \
    -Wall \
    -Werror \
    -Wextra \
    -Wno-unused-parameter \
    -fexceptions \
    -Wno-unused-private-field \
    -DIN_LIB_DEV \
		-Wno-unknown-attributes

LOCAL_SHARED_LIBRARIES := \
    liblog \
    libstructuredaidl \
    libbase \
    libbinder \
    libutils

LOCAL_C_INCLUDES := \
    $(LOCAL_PATH)/deps/rapidjson-1.1.0/include

LOCAL_EXPORT_C_INCLUDE_DIRS := \
    $(LOCAL_PATH)/clients/libmflt-reporting

include $(BUILD_SHARED_LIBRARY)

_LOCAL_PATH_B := $(LOCAL_PATH)
include $(_LOCAL_PATH_B)/app/Android.mk
include $(_LOCAL_PATH_B)/clients/Android.mk
_LOCAL_PATH_B :=
