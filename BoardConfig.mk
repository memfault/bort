ifeq ($(BORT_BOARDCONFIG_LOADED),)
  BORT_BOARDCONFIG_LOADED := 1
  BORT_PATH := vendor/memfault/bort
  BOARD_SEPOLICY_DIRS += $(BORT_PATH)/sepolicy
  ifneq (,$(wildcard $(BORT_PATH)/sepolicy/vendor-$(PLATFORM_SDK_VERSION)))
    BOARD_SEPOLICY_DIRS += $(BORT_PATH)/sepolicy/vendor-$(PLATFORM_SDK_VERSION)
  endif
  BOARD_SEPOLICY_M4DEFS += memfault_platform_sdk_version=${PLATFORM_SDK_VERSION}

  # On Android 8.1 and older (< API level 28), BOARD_PLAT_PRIVATE_SEPOLICY_DIR
  # is not allowed to hold multiple directories. A build error will occur when
  # BOARD_PLAT_PRIVATE_SEPOLICY_DIR is also used by customer/vendor code.
  # We work around this by patching system/sepolicy directly.
  ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 28 && echo true),true)
    ifneq (,$(wildcard $(BORT_PATH)/sepolicy/private-$(PLATFORM_SDK_VERSION)))
      BOARD_PLAT_PRIVATE_SEPOLICY_DIR += $(BORT_PATH)/sepolicy/private-$(PLATFORM_SDK_VERSION)
    endif
    ifneq (,$(wildcard $(BORT_PATH)/sepolicy/public-$(PLATFORM_SDK_VERSION)))
      BOARD_PLAT_PUBLIC_SEPOLICY_DIR += $(BORT_PATH)/sepolicy/public-$(PLATFORM_SDK_VERSION)
    endif

    BOARD_PLAT_PRIVATE_SEPOLICY_DIR += $(BORT_PATH)/sepolicy/private
    BOARD_PLAT_PUBLIC_SEPOLICY_DIR += $(BORT_PATH)/sepolicy/public
  endif
endif
