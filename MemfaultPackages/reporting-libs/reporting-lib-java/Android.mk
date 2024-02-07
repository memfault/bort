LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := memfault-reporting-lib
LOCAL_MODULE_TAGS := optional
LOCAL_SRC_FILES := \
    $(call all-java-files-under, src/main/java) \
    $(call all-java-files-under, ../reporting-lib-common/src/main) \
    $(call all-Iaidl-files-under, ../../../MemfaultStructuredLogd)

# <queries> tag fails compilation <30, so need a separate manifest without that.
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 30 && echo true),true)
LOCAL_MANIFEST_FILE := src/30andup/AndroidManifest.xml
else
LOCAL_MANIFEST_FILE := src/pre30/AndroidManifest.xml
endif
LOCAL_USE_AAPT2 := true

LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/src/main/res
include $(BUILD_STATIC_JAVA_LIBRARY)
