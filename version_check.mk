ifeq ($(shell test $(PLATFORM_SDK_VERSION) -ge 26 && echo true),true)
$(error "This branch of the Bort SDK only supports Android version 7. Please use the 8-10 branch for Android 8-10, master branch for later releases.")
endif
