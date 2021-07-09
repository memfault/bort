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
