BOARD_SEPOLICY_DIRS += vendor/memfault/bort/sepolicy
BOARD_SEPOLICY_M4DEFS += memfault_platform_sdk_version=${PLATFORM_SDK_VERSION}

# On Android 8.1 and older (< API level 28), BOARD_PLAT_PRIVATE_SEPOLICY_DIR
# is not allowed to hold multiple directories. A build error will occur when
# BOARD_PLAT_PRIVATE_SEPOLICY_DIR is also used by customer/vendor code.
# We work around this by patching system/sepolicy directly.
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 28 && echo true),true)
  BOARD_PLAT_PRIVATE_SEPOLICY_DIR += vendor/memfault/bort/sepolicy/private
endif
