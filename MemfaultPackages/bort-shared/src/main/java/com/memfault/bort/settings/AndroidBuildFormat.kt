package com.memfault.bort.settings

enum class AndroidBuildFormat(val id: String) {
    SYSTEM_PROPERTY_ONLY("system_property_only"),
    BUILD_FINGERPRINT_ONLY("build_fingerprint_only"),
    BUILD_FINGERPRINT_AND_SYSTEM_PROPERTY("build_fingerprint_and_system_property");

    companion object {
        fun getById(id: String) = AndroidBuildFormat.values().first { it.id == id }
    }
}
