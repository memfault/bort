LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := MemfaultDumpster
LOCAL_MODULE_CLASS := EXECUTABLES
LOCAL_SRC_FILES := \
  ContinuousLogcatConfigProto.proto \
  ContinuousLogcat.cpp \
  MemfaultDumpster.cpp \
  android-9/file.cpp
LOCAL_C_INCLUDES += $(call local-generated-sources-dir)/proto/$(LOCAL_PATH)
LOCAL_CFLAGS := -Werror -Wall -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION) -fstack-protector-all
ifeq ($(TARGET_BUILD_BORT_UNDER_TEST),1)
LOCAL_CFLAGS += -DBORT_UNDER_TEST
endif
LOCAL_CPP_FLAGS := -Wno-unused-parameter -fstack-protector-all
LOCAL_SHARED_LIBRARIES := \
  libbase \
  libbinder \
  libcutils \
  libdumpstateutil \
  libmemfault_dumpster_aidl \
  libutils \
  libservices \
  libprotobuf-cpp-full \
  libmflt-reporting
LOCAL_STATIC_LIBRARIES := liblog

# Support for storage wear info from HAL is available from 11+ on
# devices that support it
ifeq ($(call math_gt_or_eq, $(PLATFORM_SDK_VERSION), 30), true)
  LOCAL_SHARED_LIBRARIES += \
    android.hardware.health@1.0 \
    android.hardware.health@2.0
  LOCAL_STATIC_LIBRARIES += \
    libhealthhalutils \
    libhidlbase

  # Android 13 and up support AIDL and can wrap HIDL hals in a backward compatible way,
  # others are HIDL only
  ifeq ($(call math_gt_or_eq, $(PLATFORM_SDK_VERSION), 33), true)
    LOCAL_CFLAGS += -DSTORAGE_INFO_PREFER_AIDL
    LOCAL_SHARED_LIBRARIES += \
      libbinder_ndk \
      libvndksupport
    LOCAL_STATIC_LIBRARIES += \
      android.hardware.health-translate-ndk \
      libhealthshim

    # Health HAL NDK is used to wrap HIDL health HALs, but has different versions depending on the
    # platform release ids
    ifeq ($(call math_gt_or_eq, $(PLATFORM_SDK_VERSION), 35), true)
      # Android 15 has three point releases (AP3A, AP4A and BP1A) and the health hal NDK
      # version differs between them
      ifeq ($(findstring BP1A,$(BUILD_ID)),BP1A)
        LOCAL_SHARED_LIBRARIES += android.hardware.health-V4-ndk
      else
        LOCAL_SHARED_LIBRARIES += android.hardware.health-V3-ndk
      endif
    else ifeq ($(call math_gt_or_eq, $(PLATFORM_SDK_VERSION), 34), true)
      # Android 14 has three point releases (UP1A, AP1A and AP2A) and the health hal NDK
      # version differs between them
      ifeq ($(findstring UP1A,$(BUILD_ID)),UP1A)
        LOCAL_SHARED_LIBRARIES += android.hardware.health-V2-ndk
      else
        LOCAL_SHARED_LIBRARIES += android.hardware.health-V3-ndk
      endif
    else
      LOCAL_SHARED_LIBRARIES += android.hardware.health-V1-ndk
    endif
  else
    LOCAL_CFLAGS += -DSTORAGE_INFO_PREFER_HIDL
  endif
endif

LOCAL_PROTOC_OPTIMIZE_TYPE := full
LOCAL_INIT_RC := memfault_dumpster.rc
LOCAL_SYSTEM_EXT_MODULE := true
include $(BUILD_EXECUTABLE)
