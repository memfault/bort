LOCAL_PATH := $(call my-dir)

# Note: This is a makefile rather than a Blueprint because it links to libservices (a makefile module in Oreo)
#  and blueprint targets cannot link to makefile targets.

include $(CLEAR_VARS)
LOCAL_MODULE := libstructured
LOCAL_SRC_FILES := $(call all-cpp-files-under, src)  aidl/com/memfault/bort/internal/ILogger.aidl
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
LOCAL_CFLAGS += -DDEBUG_BUILD
endif
LOCAL_C_INCLUDES += $(LOCAL_PATH)/deps/rapidjson-1.1.0/include $(LOCAL_PATH)/deps/sqlite_modern_cpp-3.2/hdr/
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libbase libbinder liblog libservices libsqlite libutils
LOCAL_WHOLE_STATIC_LIBRARIES := libgtest_prod
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := MemfaultStructuredLogd
LOCAL_SRC_FILES := main.cpp aidl/com/memfault/bort/internal/ILogger.aidl
LOCAL_C_INCLUDES += $(LOCAL_PATH)/deps/rapidjson-1.1.0/include $(LOCAL_PATH)/deps/sqlite_modern_cpp-3.2/hdr/
LOCAL_MODULE_TAGS := optional
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions
ifneq (,$(filter userdebug eng, $(TARGET_BUILD_VARIANT)))
LOCAL_CFLAGS += -DDEBUG_BUILD
endif
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libbase libbinder liblog libservices libsqlite libutils libcutils
LOCAL_STATIC_LIBRARIES := libstructured
LOCAL_INIT_RC := memfault_structured_logd.rc
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := MemfaultStructuredLogdTests
LOCAL_SRC_FILES := testrunner.cpp $(call all-cpp-files-under, tests)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/src $(LOCAL_PATH)/deps/rapidjson-1.1.0/include $(LOCAL_PATH)/deps/sqlite_modern_cpp-3.2/hdr/
LOCAL_MODULE_TAGS := tests
LOCAL_CFLAGS := -Wall -Werror -Wextra -Wno-unused-parameter -fexceptions
LOCAL_CLANG := true
LOCAL_SHARED_LIBRARIES := libbase libbinder liblog libservices libsqlite libutils
LOCAL_STATIC_LIBRARIES := libstructured
include $(BUILD_NATIVE_TEST)
