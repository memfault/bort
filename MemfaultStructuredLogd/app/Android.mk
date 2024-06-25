LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under, src) \
                        ../com/memfault/bort/internal/ILogger.aidl

LOCAL_MANIFEST_FILE := src/main/AndroidManifest.xml

# Name of the APK to build
LOCAL_PACKAGE_NAME := MemfaultStructuredLogd
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PRIVATE_PLATFORM_APIS := true

LOCAL_PROGUARD_ENABLED := disabled
LOCAL_DEX_PREOPT := false

LOCAL_INIT_RC := ../memfault_structured_logd.rc

# Tell it to build an APK
include $(BUILD_PACKAGE)
