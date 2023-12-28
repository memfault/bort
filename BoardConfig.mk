BORT_PATH := vendor/memfault/bort

BORT_BOARD_SEPOLICY_DIR := $(BORT_PATH)/sepolicy
ifeq (,$(findstring $(BORT_BOARD_SEPOLICY_DIR),$(BOARD_SEPOLICY_DIRS)))
  BOARD_SEPOLICY_DIRS += $(BORT_BOARD_SEPOLICY_DIR)
endif

BORT_SEPOLICY_VENDOR_DIR := $(BORT_PATH)/sepolicy/vendor-$(PLATFORM_SDK_VERSION)
ifneq (,$(wildcard $(BORT_SEPOLICY_VENDOR_DIR)))
  ifeq (,$(findstring $(BORT_SEPOLICY_VENDOR_DIR),$(BOARD_SEPOLICY_DIRS)))
    BOARD_SEPOLICY_DIRS += $(BORT_SEPOLICY_VENDOR_DIR)
  endif
endif



BORT_BOARD_SEPOLICY_M4DEF := memfault_platform_sdk_version=${PLATFORM_SDK_VERSION}
ifeq (,$(findstring $(BORT_BOARD_SEPOLICY_M4DEF),$(BOARD_SEPOLICY_M4DEFS)))
  BOARD_SEPOLICY_M4DEFS += $(BORT_BOARD_SEPOLICY_M4DEF)
endif

# On Android 8.1 and older (< API level 28), BOARD_PLAT_PRIVATE_SEPOLICY_DIR
# is not allowed to hold multiple directories. A build error will occur when
# BOARD_PLAT_PRIVATE_SEPOLICY_DIR is also used by customer/vendor code.
# We work around this by patching system/sepolicy directly.
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 28 && echo true),true)
  # BOARD_PLAT_PRIVATE_SEPOLICY_DIR  
  BORT_BOARD_PLAT_PRIVATE_SEPOLICY_DIR := $(BORT_PATH)/sepolicy/private
  ifeq (,$(findstring $(BORT_BOARD_PLAT_PRIVATE_SEPOLICY_DIR),$(BOARD_PLAT_PRIVATE_SEPOLICY_DIR)))
    BOARD_PLAT_PRIVATE_SEPOLICY_DIR += $(BORT_BOARD_PLAT_PRIVATE_SEPOLICY_DIR)
  endif
  BORT_BOARD_PLAT_PRIVATE_VERSION_SEPOLICY_DIR := $(BORT_PATH)/sepolicy/private-$(PLATFORM_SDK_VERSION)
  ifneq (,$(wildcard $(BORT_BOARD_PLAT_PRIVATE_VERSION_SEPOLICY_DIR)))
    ifeq (,$(findstring $(BORT_BOARD_PLAT_PRIVATE_VERSION_SEPOLICY_DIR),$(BOARD_PLAT_PRIVATE_SEPOLICY_DIR)))
      BOARD_PLAT_PRIVATE_SEPOLICY_DIR += $(BORT_BOARD_PLAT_PRIVATE_VERSION_SEPOLICY_DIR)
    endif
  endif

  # BOARD_PLAT_PUBLIC_SEPOLICY_DIR
  BORT_BOARD_PLAT_PUBLIC_SEPOLICY_DIR := $(BORT_PATH)/sepolicy/public
  ifeq (,$(findstring $(BORT_BOARD_PLAT_PUBLIC_SEPOLICY_DIR),$(BOARD_PLAT_PUBLIC_SEPOLICY_DIR)))
    BOARD_PLAT_PUBLIC_SEPOLICY_DIR += $(BORT_BOARD_PLAT_PUBLIC_SEPOLICY_DIR)
  endif
  BORT_BOARD_PLAT_PUBLIC_VERSION_SEPOLICY_DIR := $(BORT_PATH)/sepolicy/public-$(PLATFORM_SDK_VERSION)
  ifneq (,$(wildcard $(BORT_BOARD_PLAT_PUBLIC_VERSION_SEPOLICY_DIR)))
    ifeq (,$(findstring $(BORT_BOARD_PLAT_PUBLIC_VERSION_SEPOLICY_DIR),$(BOARD_PLAT_PUBLIC_SEPOLICY_DIR)))
      BOARD_PLAT_PUBLIC_SEPOLICY_DIR += $(BORT_BOARD_PLAT_PUBLIC_VERSION_SEPOLICY_DIR)
    endif
  endif
endif

$(warning BOARD_SEPOLICY_DIRS - $(BOARD_SEPOLICY_DIRS))
$(warning BOARD_SEPOLICY_M4DEFS - $(BORT_BOARD_SEPOLICY_M4DEF))
$(warning BOARD_PLAT_PRIVATE_SEPOLICY_DIR - $(BOARD_PLAT_PRIVATE_SEPOLICY_DIR))
$(warning BOARD_PLAT_PUBLIC_SEPOLICY_DIR - $(BOARD_PLAT_PUBLIC_SEPOLICY_DIR))
