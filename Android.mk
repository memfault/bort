LOCAL_PATH := $(call my-dir)

bort-sepolicy-canary-check: selinux_policy $(shell test $(PLATFORM_SDK_VERSION) -lt 28 && echo nonplat_seapp_contexts)
	$(hide) (grep memfault_dumpster_service $(TARGET_OUT_INTERMEDIATES)/ETC/*_service_contexts_intermediates/*_service_contexts > /dev/null || ( \
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
