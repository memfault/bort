MEMFAULT_PACKAGES_DIR := $(realpath $(dir $(abspath $(lastword $(MAKEFILE_LIST)))))
BORT_PROPERTIES := $(MEMFAULT_PACKAGES_DIR)/bort.properties
BORT_SRC_GEN_TOOL := $(MEMFAULT_PACKAGES_DIR)/bort_src_gen.py

define bort_src_gen_template
$(2): PRIVATE_PATH := $(MEMFAULT_PACKAGES_DIR)
$(2): PRIVATE_CUSTOM_TOOL := $(BORT_SRC_GEN_TOOL) template $(1) $(2) $(BORT_PROPERTIES)
$(2): $(1) $(BORT_SRC_GEN_TOOL) $(BORT_PROPERTIES)
	$$(transform-generated-source)
endef

define bort_src_gen
$(eval $(call bort_src_gen_template,$(1),$(2)))
endef
