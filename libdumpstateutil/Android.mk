LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libdumpstateutil
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional

# Source files
LOCAL_SRC_FILES := \
    DumpstateInternal.cpp \
    DumpstateUtil.cpp

# Include directories to export
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)

# Shared library dependencies
LOCAL_SHARED_LIBRARIES := \
    libbase \
    liblog

# Exported shared library headers
LOCAL_EXPORT_SHARED_LIBRARY_HEADERS := \
    libbase

include $(BUILD_SHARED_LIBRARY)
