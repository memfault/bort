LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
BORT_PROPERTIES := $(LOCAL_PATH)/../MemfaultPackages/bort.properties
BORT_GEN_SCRIPT := $(LOCAL_PATH)/../MemfaultPackages/bort_src_gen.py
BORT_GEN_HEADER := $(intermediates)/bort_properties.h

$(BORT_GEN_HEADER): $(BORT_PROPERTIES) $(BORT_GEN_SCRIPT)
	@echo "Generating bort_properties.h"
	$(hide) mkdir -p $(dir $@)
	$(hide) $(BORT_GEN_SCRIPT) cpp-header $@ $<

include $(CLEAR_VARS)

LOCAL_MODULE := MemfaultDumpstateRunner
LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := \
    MemfaultDumpstateRunner.cpp \
    android-9/file.cpp

LOCAL_CFLAGS := \
    -Werror \
    -Wall

LOCAL_CPPFLAGS := \
    -Wno-unused-parameter

# There’s no product_variables support in Android.mk.
# You can inject PLATFORM_SDK_VERSION using a CFLAG from the environment if needed:
# Example:
#   export PLATFORM_SDK_VERSION=25
#   and append manually:
ifdef PLATFORM_SDK_VERSION
LOCAL_CFLAGS += -DPLATFORM_SDK_VERSION=$(PLATFORM_SDK_VERSION)
endif

LOCAL_SHARED_LIBRARIES := \
    libbase \
    libcutils \
    libdumpstateutil \
    liblog

LOCAL_GENERATED_SOURCES := $(BORT_GEN_HEADER)
LOCAL_C_INCLUDES := $(dir $(BORT_GEN_HEADER))

# Install init.rc file
LOCAL_INIT_RC := memfault_init.rc

include $(BUILD_EXECUTABLE)
