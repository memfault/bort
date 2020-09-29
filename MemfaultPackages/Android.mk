LOCAL_PATH := $(call my-dir)

include $(LOCAL_PATH)/bort_src_gen.mk

# MemfaultBort.apk
################################################################################

include $(CLEAR_VARS)

LOCAL_MODULE := com.memfault.bort.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
BORT_XML_TARGET := $(LOCAL_MODULE_PATH)/$(LOCAL_MODULE)
$(call bort_src_gen,$(LOCAL_PATH)/com.memfault.bort.xml.in,$(BORT_XML_TARGET))
LOCAL_ADDITIONAL_DEPENDENCIES := $(BORT_XML_TARGET)
include $(BUILD_PHONY_PACKAGE)


include $(CLEAR_VARS)
# Module name should match apk name to be installed (without the .apk extension)
LOCAL_MODULE := MemfaultBort
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/$(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

LOCAL_PACKAGE_NAME := MemfaultBort
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_PRIVILEGED_MODULE := true
LOCAL_REQUIRED_MODULES := com.memfault.bort.xml

# The priv-app folder
TARGET_OUT_DATA_APPS_PRIVILEGED := $(TARGET_OUT_DATA)/priv-app
include $(BUILD_PREBUILT)


# MemfaultUsageReporter.apk
################################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := com.memfault.usagereporter.xml
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions
USAGE_REPORTER_XML_TARGET := $(LOCAL_MODULE_PATH)/$(LOCAL_MODULE)
$(call bort_src_gen,$(LOCAL_PATH)/com.memfault.usagereporter.xml.in,$(USAGE_REPORTER_XML_TARGET))
LOCAL_ADDITIONAL_DEPENDENCIES := $(USAGE_REPORTER_XML_TARGET)
include $(BUILD_PHONY_PACKAGE)


include $(CLEAR_VARS)
# Module name should match apk name to be installed (without the .apk extension)
LOCAL_MODULE := MemfaultUsageReporter
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

LOCAL_PACKAGE_NAME := MemfaultUsageReporter
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_REQUIRED_MODULES := com.memfault.usagereporter.xml

# The priv-app folder
TARGET_OUT_DATA_APPS_PRIVILEGED := $(TARGET_OUT_DATA)/priv-app
include $(BUILD_PREBUILT)
