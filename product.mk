BORT_PATH := vendor/memfault/bort
GRADLE_PROPERTIES := MemfaultPackages/gradle.properties
BORT_MAJOR_VERSION := $(shell cat $(BORT_PATH)/$(GRADLE_PROPERTIES) | grep UPSTREAM_MAJOR_VERSION | cut -f2 -d"=")
BORT_MINOR_VERSION := $(shell cat $(BORT_PATH)/$(GRADLE_PROPERTIES) | grep UPSTREAM_MINOR_VERSION | cut -f2 -d"=")
BORT_PATCH_VERSION := $(shell cat $(BORT_PATH)/$(GRADLE_PROPERTIES) | grep UPSTREAM_PATCH_VERSION | cut -f2 -d"=")
BORT_VERSION := $(BORT_MAJOR_VERSION).$(BORT_MINOR_VERSION).$(BORT_PATCH_VERSION)

PRODUCT_PROPERTY_OVERRIDES += \
  vendor.memfault.bort.version.sdk=$(BORT_VERSION)

PRODUCT_PACKAGES += \
  MemfaultBort \
  MemfaultDumpstateRunner \
  MemfaultDumpster \
  MemfaultStructuredLogd \
  MemfaultUsageReporter \

TARGET_USES_MFLT_OTA ?= 0
TARGET_BUILD_BORT_UNDER_TEST ?= 0

ifeq ($(TARGET_USES_MFLT_OTA),1)
    $(warning "Building with MemfaultBortOta")
    PRODUCT_PACKAGES += MemfaultBortOta
endif

ifeq ($(TARGET_BUILD_BORT_UNDER_TEST),1)
    $(warning "Building with Memfault test dependencies")
    PRODUCT_PACKAGES += structured-client-example-c \
                        structured-client-example-cpp \
                        reporting-client-example-cpp \
                        reporting-client-example-c \
                        MemfaultStructuredLogdTests
endif
