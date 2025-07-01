LOCAL_PATH := $(call my-dir)

include $(LOCAL_PATH)/bort_src_gen.mk

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 30 && echo true),true)
  _BORT_ETC_PATH := $(TARGET_OUT_SYSTEM_EXT_ETC)
else
  _BORT_ETC_PATH := $(TARGET_OUT_ETC)
endif


# Check that ensures MemfaultBort.x509.pem matches signature of MemfaultBort.apk
################################################################################
include $(CLEAR_VARS)
LOCAL_MODULE := CheckMemfaultBortSignature
LOCAL_MODULE_PATH := $(HOST_OUT_INTERMEDIATES)/memfault/bort
BORT_CHECK_OUTPUT_TARGET := $(LOCAL_MODULE_PATH)/signature-check.txt
$(call bort_check_signature,$(BORT_CHECK_OUTPUT_TARGET),$(LOCAL_PATH)/MemfaultBort.apk,$(LOCAL_PATH)/MemfaultBort.x509.pem)
LOCAL_ADDITIONAL_DEPENDENCIES := $(BORT_CHECK_OUTPUT_TARGET)
include $(BUILD_PHONY_PACKAGE)

# Check that ensures MemfaultBortOta.x509.pem matches signature of MemfaultBortOta.apk
################################################################################
include $(CLEAR_VARS)
LOCAL_MODULE := CheckMemfaultBortOtaSignature
LOCAL_MODULE_PATH := $(HOST_OUT_INTERMEDIATES)/memfault/bort
BORT_CHECK_OUTPUT_TARGET := $(LOCAL_MODULE_PATH)/ota-signature-check.txt
$(call bort_check_signature,$(BORT_CHECK_OUTPUT_TARGET),$(LOCAL_PATH)/MemfaultBortOta.apk,$(LOCAL_PATH)/MemfaultBortOta.x509.pem)
LOCAL_ADDITIONAL_DEPENDENCIES := $(BORT_CHECK_OUTPUT_TARGET)
include $(BUILD_PHONY_PACKAGE)


# MemfaultBort.apk
################################################################################

include $(CLEAR_VARS)

LOCAL_MODULE := com.memfault.bort.xml
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(_BORT_ETC_PATH)/permissions
BORT_XML_TARGET := $(call local-generated-sources-dir)/$(LOCAL_MODULE)
$(call bort_src_gen,$(LOCAL_PATH)/com.memfault.bort.xml.in,$(BORT_XML_TARGET))
LOCAL_SRC_FILES := ../../../../$(BORT_XML_TARGET)
include $(BUILD_PREBUILT)


include $(CLEAR_VARS)
# Module name should match apk name to be installed (without the .apk extension)
LOCAL_MODULE := MemfaultBort
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/$(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
# Android 12 and above needs us to list any merged manifest <uses-library> entries.
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 31 && echo true),true)
LOCAL_OPTIONAL_USES_LIBRARIES := androidx.window.extensions androidx.window.sidecar
endif

LOCAL_PACKAGE_NAME := MemfaultBort
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_PRIVILEGED_MODULE := true
LOCAL_REQUIRED_MODULES := com.memfault.bort.xml CheckMemfaultBortSignature

# The priv-app folder
TARGET_OUT_DATA_APPS_PRIVILEGED := $(TARGET_OUT_DATA)/priv-app
LOCAL_SYSTEM_EXT_MODULE := true
include $(BUILD_PREBUILT)

# MemfaultBortOta.apk
################################################################################

include $(CLEAR_VARS)

LOCAL_MODULE := com.memfault.bort.ota.xml
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(_BORT_ETC_PATH)/permissions
BORT_XML_TARGET := $(call local-generated-sources-dir)/$(LOCAL_MODULE)
$(call bort_src_gen,$(LOCAL_PATH)/com.memfault.bort.ota.xml.in,$(BORT_XML_TARGET))
LOCAL_SRC_FILES := ../../../../$(BORT_XML_TARGET)
include $(BUILD_PREBUILT)


include $(CLEAR_VARS)
# Module name should match apk name to be installed (without the .apk extension)
LOCAL_MODULE := MemfaultBortOta
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_REPLACE_PREBUILT_APK_INSTALLED := $(LOCAL_PATH)/$(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
# Android 12 and above needs us to list any merged manifest <uses-library> entries.
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 31 && echo true),true)
LOCAL_OPTIONAL_USES_LIBRARIES := androidx.window.extensions androidx.window.sidecar
endif

LOCAL_PACKAGE_NAME := MemfaultBortOta
LOCAL_CERTIFICATE := PRESIGNED
LOCAL_PRIVILEGED_MODULE := true
LOCAL_REQUIRED_MODULES := com.memfault.bort.ota.xml CheckMemfaultBortOtaSignature

# The priv-app folder
TARGET_OUT_DATA_APPS_PRIVILEGED := $(TARGET_OUT_DATA)/priv-app
LOCAL_SYSTEM_EXT_MODULE := true
include $(BUILD_PREBUILT)


# MemfaultUsageReporter.apk
################################################################################

include $(CLEAR_VARS)
LOCAL_MODULE := com.memfault.usagereporter.xml
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(_BORT_ETC_PATH)/permissions
USAGE_REPORTER_XML_TARGET := $(call local-generated-sources-dir)/$(LOCAL_MODULE)
$(call bort_src_gen,$(LOCAL_PATH)/com.memfault.usagereporter.xml.in,$(USAGE_REPORTER_XML_TARGET))
LOCAL_SRC_FILES := ../../../../$(USAGE_REPORTER_XML_TARGET)
include $(BUILD_PREBUILT)


include $(CLEAR_VARS)
# Module name should match apk name to be installed (without the .apk extension)
LOCAL_MODULE := MemfaultUsageReporter
LOCAL_SRC_FILES := $(LOCAL_MODULE).apk
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
# Android 12 and above needs us to list any merged manifest <uses-library> entries.
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 31 && echo true),true)
LOCAL_OPTIONAL_USES_LIBRARIES := androidx.window.extensions androidx.window.sidecar
endif

LOCAL_PACKAGE_NAME := MemfaultUsageReporter
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_REQUIRED_MODULES := com.memfault.usagereporter.xml

# The priv-app folder
TARGET_OUT_DATA_APPS_PRIVILEGED := $(TARGET_OUT_DATA)/priv-app
LOCAL_SYSTEM_EXT_MODULE := true
include $(BUILD_PREBUILT)

include $(LOCAL_PATH)/reporting-libs/reporting-lib-java/Android.mk
