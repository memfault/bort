ifeq ($(shell test $(PLATFORM_SDK_VERSION) -le 29 && echo true),true)
$(error "This branch of the Bort SDK only supports Android versions 11 onwards. Please use the 8-10 branch.")
endif
