LOCAL_PATH := $(call my-dir)

BORT_SEPOLICY_CANARY_DEPS :=

SERVICE_CONTEXTS_GLOB := $(TARGET_OUT_INTERMEDIATES)/ETC/*service_contexts_intermediates/*service_contexts

# Android  16+ uses soong to build sepolicy, so check the generated artifacts instead of make intermediates
ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 36 && echo true), true)
	SERVICE_CONTEXTS_GLOB := $(TARGET_OUT_SYSTEM_EXT)/etc/selinux/*service_contexts
endif

ifeq ($(shell test $(PLATFORM_SDK_VERSION) -lt 26 && echo true), true)
	BORT_SEPOLICY_CANARY_DEPS := sepolicy service_contexts
else ifeq ($(shell test $(PLATFORM_SDK_VERSION) -lt 28 && echo true), true)
	BORT_SEPOLICY_CANARY_DEPS := selinux_policy nonplat_seapp_contexts
else
	BORT_SEPOLICY_CANARY_DEPS := selinux_policy
endif


bort-sepolicy-canary-check: $(BORT_SEPOLICY_CANARY_DEPS)
	$(hide) (grep memfault_dumpster_service $(SERVICE_CONTEXTS_GLOB) > /dev/null || ( \
					echo "==========" 1>&2; \
					echo "ERROR: memfault_dumpster_service not found in sepolicy contexts" 1>&2; \
					echo "       this usually indicates an integration issue. Please ensure" 1>&2; \
					echo "       that definitions from vendor/memfault/bort/BoardConfig.mk" 1>&2; \
					echo "       are correctly included and not overwritten." 1>&2; \
					echo "See https://mflt.io/android-sepolicy" 1>&2; \
					exit 1; ))

.PHONY: bort-sepolicy-canary-check

droidcore: bort-sepolicy-canary-check

include $(call all-subdir-makefiles)
