ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 30 && echo true),true)
$(error "This branch of the Bort SDK only supports Android versions 8-10. Please use the master branch.")
endif
