LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := memfault-reporting-lib
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src/main/java) \
    $(call all-java-files-under, ../reporting-lib-common/src/main) \
    $(call all-Iaidl-files-under, ../../../MemfaultStructuredLogd)
include $(BUILD_STATIC_JAVA_LIBRARY)
