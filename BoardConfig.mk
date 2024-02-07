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

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -le 33 && echo true),true)
include $(BORT_PATH)/sepolicy_8_to_13.mk
else
include $(BORT_PATH)/sepolicy_14.mk
endif
