#ifndef STORAGE_H
#define STORAGE_H

// #define LOG_NDEBUG 0
#include <log/log.h>

#include <android-base/file.h>
#include <android-base/strings.h>
#include <binder/Status.h>
#include <cstdio>
#include <string>

using namespace android::base;

#ifdef STORAGE_INFO_PREFER_AIDL
#include <android/binder_manager.h>
#include <aidl/android/hardware/health/IHealth.h>
#include <health-shim/shim.h>
#include <healthhalutils/HealthHalUtils.h>

using aidl::android::hardware::health::HealthShim;
using aidl::android::hardware::health::IHealth;
using aidl::android::hardware::health::StorageInfo;
using android::hardware::health::V2_0::get_health_service;
#endif

#ifdef STORAGE_INFO_PREFER_HIDL
#include <android/hardware/health/2.0/IHealth.h>
#include <healthhalutils/HealthHalUtils.h>

using android::hardware::health::V2_0::IHealth;
using android::hardware::health::V2_0::Result;
using android::hardware::health::V2_0::StorageInfo;
using android::hardware::health::V2_0::get_health_service;
#endif


namespace memfault {

// These are the fallback paths from storaged for devices that do not have
// storage info support in the health hal.

// UFS fallback path
const std::string ufs_health_file = "/sys/devices/soc/624000.ufshc/health";

// eMMC fallback path
const std::string emmc_health_file_dir = "/sys/bus/mmc/devices/mmc0:0001/";
const std::string emmc_health_file_versions[] = {
    "4.0",
    "4.1",
    "4.2",
    "4.3",
    "Obsolete",
    "4.41",
    "4.5",
    "5.0",
    "5.1",
};

// JEDEC standard No.84-B50 flash lifetime information
// Refer to https://www.notion.so/memfault/Flash-Wear-Tracking-Design-1c395bc8429380f9ab31dfdf77beb582?source=copy_link#1c795bc8429380ab8297fc57315de463
struct jedec_storage_info {
    // Custom name to differentiate the device or mechanism that the storage info was acquired from.
    std::string source;
    int eol;
    int lifetimeA;
    int lifetimeB;
    std::string version;
};

#ifdef STORAGE_INFO_PREFER_AIDL
static bool _get_storage_info_from_aidl_hal(jedec_storage_info &info) {
    std::shared_ptr<aidl::android::hardware::health::IHealth> aidl_health;
    android::sp<android::hardware::health::V2_0::IHealth> hidl_health;

    auto service_name = std::string(IHealth::descriptor) + "/default";
    if (AServiceManager_isDeclared(service_name.c_str())) {
        ndk::SpAIBinder binder(
            AServiceManager_waitForService(service_name.c_str()));
        aidl_health = IHealth::fromBinder(binder);
        if (aidl_health == nullptr) {
            ALOGW(" AIDL health service is declared but cannot be retrieved");
        }
    }
    if (aidl_health == nullptr) {
        ALOGI("Unable to get AIDL health service, falling back to HIDL");
        hidl_health = get_health_service();
        if (hidl_health != nullptr) {
            aidl_health = ndk::SharedRefBase::make<HealthShim>(hidl_health);
        } else {
            ALOGW("Cannot retrieve HIDL health service, no more HALs available");
            return false;
        }
    }

    std::vector<StorageInfo> halInfos;
    auto ret = aidl_health->getStorageInfo(&halInfos);
    if (ret.isOk()) {
        if (halInfos.size() != 0) {
            info.source = "HealthHAL";
            info.eol = halInfos[0].eol;
            info.lifetimeA = halInfos[0].lifetimeA;
            info.lifetimeB = halInfos[0].lifetimeB;
            info.version = halInfos[0].version;
            return true;
        }
        ALOGE("getStorageInfo returned empty vector");
        return false;
    }
    if (ret.getExceptionCode() == android::binder::Status::EX_UNSUPPORTED_OPERATION) {
        ALOGW("getStorageInfo not supported by the HAL");
    } else {
        ALOGE("getStorageInfo failed with error: %s", ret.getDescription().c_str());
    }
    return false;
}
#endif

#ifdef STORAGE_INFO_PREFER_HIDL
static bool _get_storage_info_from_hidl_hal(jedec_storage_info &info) {
    android::sp<android::hardware::health::V2_0::IHealth> hidl_health = get_health_service();
    if (hidl_health == nullptr) {
        ALOGW("Cannot retrieve HIDL health service");
        return false;
    }

    auto ret = hidl_health->getStorageInfo([&info](auto result, const auto& halInfos) {
        if (result == Result::NOT_SUPPORTED) {
            ALOGE("getStorageInfo is not supported on this device");
            return;
        }
        if (result != Result::SUCCESS || halInfos.size() == 0) {
            ALOGE("getStorageInfo failed with error: %d and size %zu", static_cast<int>(result), halInfos.size());
            return;
        }

        info.source = "HealthHAL";
        info.eol = halInfos[0].eol;
        info.lifetimeA = halInfos[0].lifetimeA;
        info.lifetimeB = halInfos[0].lifetimeB;
        info.version = halInfos[0].version;
    });

    if (!ret.isOk()) {
        ALOGE("getStorageInfo failed with error: %s", ret.description().c_str());
        return false;
    } else {
        if (info.eol == 0 && info.lifetimeA == 0 && info.lifetimeB == 0) {
            ALOGE("getStorageInfo captured invalid data");
            return false;
        }
        return true;
    }
}
#endif

static bool _get_storage_info_from_hal(jedec_storage_info &info) {
#if defined(STORAGE_INFO_PREFER_AIDL)
    return _get_storage_info_from_aidl_hal(info);
#elif defined(STORAGE_INFO_PREFER_HIDL)
    return _get_storage_info_from_hidl_hal(info);
#else
    ALOGW("Getting wear info from HAL is not supported in this platform version");
    (void)info; // suppress unused variable warning
    return false;
#endif
}

static bool _get_storage_info_from_emmc(jedec_storage_info &info) {
    int eol = 0, lifetimeA = 0, lifetimeB = 0;
    std::string version;

    std::string buffer;
    uint16_t rev = 0;
    if (!ReadFileToString(emmc_health_file_dir + "rev", &buffer)) {
        ALOGD("Failed to read eMMC health file: %s", emmc_health_file_dir.c_str());
        return false;
    }

    if (sscanf(buffer.c_str(), "%hx", &rev) < 1 || rev < 7 || rev >= sizeof(emmc_health_file_versions)/sizeof(emmc_health_file_versions[0])) {
        ALOGD("Cannot read or parse unsupported eMMC rev: %d", rev);
        return false;
    }

    version = "emmc " + emmc_health_file_versions[rev];

    if (!ReadFileToString(emmc_health_file_dir + "pre_eol_info", &buffer)) {
        ALOGD("Cannot find pre_eol_info file at %s", emmc_health_file_dir.c_str());
        return false;
    }

    if (sscanf(buffer.c_str(), "%x", &eol) < 1 || eol == 0) {
        ALOGD("Cannot read or parse pre_eol_info file at %s", emmc_health_file_dir.c_str());
        return false;
    }

    if (!ReadFileToString(emmc_health_file_dir + "life_time", &buffer)) {
        ALOGD("Cannot find life_time file at %s", emmc_health_file_dir.c_str());
        return false;
    }

    if (sscanf(buffer.c_str(), "0x%x 0x%x", &lifetimeA, &lifetimeB) < 2 ||
        (lifetimeA == 0 && lifetimeB == 0)) {
        ALOGD("Cannot read or parse life_time file at %s", emmc_health_file_dir.c_str());
        return false;
    }

    info.source = "mmc0";
    info.eol = eol;
    info.lifetimeA = lifetimeA;
    info.lifetimeB = lifetimeB;
    info.version = version;

    return true;
}

static bool _get_storage_info_from_ufs(jedec_storage_info &info) {
    int eol = 0, lifetimeA = 0, lifetimeB = 0;
    std::string version;

    std::string buffer;
    if (!android::base::ReadFileToString(ufs_health_file, &buffer)) {
        ALOGD("Failed to read UFS health file: %s", ufs_health_file.c_str());
        return false;
    }

    std::vector<std::string> lines = android::base::Split(buffer, "\n");
    if (lines.empty()) {
        ALOGD("UFS health file is empty: %s", ufs_health_file.c_str());
        return false;
    }

    char rev[8];
    if (sscanf(lines[0].c_str(), "Revision: 0x%7s\n", rev) != 1) {
        ALOGD("Failed to parse UFS health file: %s", ufs_health_file.c_str());
        return false;
    }

    version = "ufs " + std::string(rev);
    for (size_t i = 1; i < lines.size(); ++i) {
        char token[32];
        uint16_t val;
        int ret;
        if ((ret = sscanf(lines[i].c_str(),
                          "Health Descriptor[Byte offset 0x%*d]: %31s = 0x%hx",
                          token, &val)) != 2) {
            continue;
        }

        if (std::string(token) == "bPreEOLInfo") {
            eol = val;
        } else if (std::string(token) == "bDeviceLifeTimeEstA") {
            lifetimeA = val;
        } else if (std::string(token) == "bDeviceLifeTimeEstB") {
            lifetimeB = val;
        }
    }

    if (eol == 0 && lifetimeA == 0 && lifetimeB == 0) {
        ALOGD("Failed to parse UFS health file: %s", ufs_health_file.c_str());
        return false;
    }

    info.source = "624000.ufshc";
    info.eol = eol;
    info.lifetimeA = lifetimeA;
    info.lifetimeB = lifetimeB;
    info.version = version;

    return true;
}


bool get_storage_info(jedec_storage_info &info) {

    // first attempt to get from the health hal
    if (_get_storage_info_from_hal(info)) {
        return true;
    }

    // if that fails get it from the eMMC sysfs node
    if (_get_storage_info_from_emmc(info)) {
        return true;
    }

    // if that fails get it from the ufs sysfs node
    if (_get_storage_info_from_ufs(info)) {
        return true;
    }

    return false;
}

} // namespace memfault

#endif
