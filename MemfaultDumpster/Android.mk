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
LOCAL_PROTOC_OPTIMIZE_TYPE := full
LOCAL_STATIC_LIBRARIES := liblog
LOCAL_INIT_RC := memfault_dumpster.rc
include $(BUILD_EXECUTABLE)
