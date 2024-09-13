LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

# Build all java files in the java subdirectory
LOCAL_SRC_FILES := $(call all-subdir-java-files)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/app/src/main/res
LOCAL_MANIFEST_FILE := app/src/main/AndroidManifest.xml

LOCAL_STATIC_ANDROID_LIBRARIES := memfault-reporting-lib

# Name of the APK to build
LOCAL_PACKAGE_NAME := ReportingJavaTestApp
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_SYSTEM_EXT_MODULE := true
# Tell it to build an APK
include $(BUILD_PACKAGE)
