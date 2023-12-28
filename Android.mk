LOCAL_PATH := $(call my-dir)

bort-sepolicy-canary-check: selinux_policy
	$(hide) (grep memfault_dumpster_service $(TARGET_OUT_INTERMEDIATES)/ETC/*_service_contexts_intermediates/*_service_contexts > /dev/null || ( \
					echo "==========" 1>&2; \
					echo "ERROR: memfault_dumpster_service not found in sepolicy contexts" 1>&2; \
					echo "       this usually indicates an integration issue. Please ensure" 1>&2; \
					echo "       that definitions from vendor/memfault/bort/BoardConfig.mk" 1>&2; \
					echo "       are correctly included and not overwriten." 1>&2; \
					echo "See https://mflt.io/android-sepolicy" 1>&2; \
					exit 1; )) \
                && grep bort_app_data_file $(TARGET_OUT_INTERMEDIATES)/ETC/*_seapp_contexts_intermediates/*_seapp_contexts > /dev/null || ( \
					echo "==========" 1>&2; \
					echo "ERROR: bort_app_data_file not found in sepolicy contexts" 1>&2; \
					echo "       this usually indicates an integration issue. Please ensure" 1>&2; \
					echo "       that definitions from vendor/memfault/bort/BoardConfig.mk" 1>&2; \
					echo "       are correctly included and not overwriten." 1>&2; \
					echo "See https://mflt.io/android-sepolicy" 1>&2; \
					exit 1; )

.PHONY: bort-sepolicy-canary-check

droidcore: bort-sepolicy-canary-check

include $(call all-subdir-makefiles)
