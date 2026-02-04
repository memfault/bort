ifeq ($(shell [ $(PLATFORM_SDK_VERSION) -lt 26 -o $(PLATFORM_SDK_VERSION) -ge 30 ] && echo true),true)
$(error "This branch of the Bort SDK only supports Android versions 8-10. Please use the 7 branch for Android 7, master for later versions")
endif
