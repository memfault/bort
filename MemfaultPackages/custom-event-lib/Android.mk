LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := memfault-custom-event-lib
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src/main/java) \
    $(call all-Iaidl-files-under, ../../MemfaultStructuredLogd/aidl)
include $(BUILD_STATIC_JAVA_LIBRARY)
