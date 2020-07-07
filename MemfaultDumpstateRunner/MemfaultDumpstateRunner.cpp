// Based on https://cs.android.com/android/_/android/platform/packages/services/Car/+/2a84c7a9fe001e111778ccda84af1580a082b3e0:car-bugreportd/main.cpp;drc=552164f80e8f503683cb71f03895908d35bad0f3
/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "MemfaultDumpstateRunner"

#include <android-base/errors.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/macros.h>
#include <android-base/properties.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <android-base/unique_fd.h>
#include <cutils/sockets.h>
#include <DumpstateUtil.h>
#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <glob.h>
#include <log/log_main.h>
#include <private/android_filesystem_config.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include <chrono>
#include <string>
#include <vector>

#include "android-9/file.h"

#define TARGET_APP_ID "vnd.myandroid.bortappid"
#define TARGET_COMPONENT_CLASS "com.memfault.bort.receivers.BugReportReceiver"
#define TARGET_COMPONENT TARGET_APP_ID "/" TARGET_COMPONENT_CLASS
#define TARGET_DIR "/data/data/" TARGET_APP_ID "/files/bugreports/"

using android::os::dumpstate::CommandOptions;
using android::os::dumpstate::RunCommandToFd;

namespace {
// The prefix used by bugreportz protocol to indicate bugreport finished successfully.
constexpr const char* kOkPrefix = "OK:";
// Number of connect attempts to dumpstate socket
constexpr const int kMaxDumpstateConnectAttempts = 20;
// Wait time between connect attempts
constexpr const int kWaitTimeBetweenConnectAttemptsInSec = 1;
// Wait time for dumpstate. Set a timeout so that if nothing is read in 10 minutes, we'll stop
// reading and quit. No timeout in dumpstate is longer than 60 seconds, so this gives lots of leeway
// in case of unforeseen time outs.
constexpr const int kDumpstateTimeoutInSec = 600;

// Processes the given dumpstate progress protocol |line| and updates
// |out_last_nonempty_line| when |line| is non-empty, and |out_zipPath| when
// the bugreport is finished.
void processLine(const std::string& line, std::string* out_zipPath,
                 std::string* out_last_nonempty_line) {
    // The protocol is documented in frameworks/native/cmds/bugreportz/readme.md
    if (line.empty()) {
        return;
    }
    *out_last_nonempty_line = line;
    if (line.find(kOkPrefix) != 0) {
        return;
    }
    *out_zipPath = line.substr(strlen(kOkPrefix));
    return;
}

int copyTo(int fd_in, void* buffer, size_t buffer_len) {
    ssize_t bytes_read = TEMP_FAILURE_RETRY(read(fd_in, buffer, buffer_len));
    if (bytes_read == 0) {
        return 0;
    }
    if (bytes_read == -1) {
        // EAGAIN really means time out, so make that clear.
        if (errno == EAGAIN) {
            ALOGE("read timed out");
        } else {
            ALOGE("read terminated abnormally (%s)", strerror(errno));
        }
        return -1;
    }
    return bytes_read;
}

bool copyFile(const std::string& in_path, const std::string& out_path) {
    android::base::unique_fd input_fd(TEMP_FAILURE_RETRY(open(in_path.c_str(), O_RDONLY)));
    if (input_fd == -1) {
        ALOGE("Failed to open input file %s.", in_path.c_str());
        return false;
    }
    android::base::unique_fd output_fd(TEMP_FAILURE_RETRY(open(
            out_path.c_str(),
            O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW,
            S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH)));
    if (output_fd == -1) {
        ALOGE("Failed to open output file %s.", out_path.c_str());
        return false;
    }
    while (1) {
        char buffer[65536];
        const ssize_t bytes_read = copyTo(input_fd, buffer, sizeof(buffer));
        if (bytes_read == 0) {
            break;
        }
        if (bytes_read == -1) {
            ALOGE("Failed to read from %s", in_path.c_str());
            return false;
        }
        if (!android::base::WriteFully(output_fd, buffer, bytes_read)) {
            ALOGE("Failed to write to %s", out_path.c_str());
            return false;
        }
    }
    return true;
}

// Triggers a bugreport and waits until it is all collected.
// returns false if error, true if success
bool doBugreport(size_t* out_bytesWritten, std::string* zipPath) {
    // Socket will not be available until service starts.
    android::base::unique_fd s;
    for (int i = 0; i < kMaxDumpstateConnectAttempts; i++) {
        s.reset(socket_local_client("dumpstate", ANDROID_SOCKET_NAMESPACE_RESERVED, SOCK_STREAM));
        if (s != -1) break;
        sleep(kWaitTimeBetweenConnectAttemptsInSec);
    }

    if (s == -1) {
        ALOGE("failed to connect to dumpstatez service");
        return false;
    }

    // Set a timeout so that if nothing is read by the timeout, stop reading and quit
    struct timeval tv = {
        .tv_sec = kDumpstateTimeoutInSec,
        .tv_usec = 0,
    };
    if (setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) != 0) {
        ALOGW("Cannot set socket timeout (%s)", strerror(errno));
    }

    std::string line;
    std::string last_nonempty_line;
    char buffer[65536];
    while (true) {
        ssize_t bytes_read = copyTo(s, buffer, sizeof(buffer));
        if (bytes_read == 0) {
            break;
        }
        if (bytes_read == -1) {
            ALOGE("Failed to copy progress to the progress_socket.");
            return false;
        }
        // Process the buffer line by line. this is needed for the filename.
        for (int i = 0; i < bytes_read; i++) {
            char c = buffer[i];
            if (c == '\n') {
                processLine(line, zipPath, &last_nonempty_line);
                line.clear();
            } else {
                line.append(1, c);
            }
        }
        *out_bytesWritten += bytes_read;
    }
    s.reset();
    // Process final line, in case it didn't finish with newline.
    processLine(line, zipPath, &last_nonempty_line);
    // if doBugReport finished successfully, zip path should be set.
    if (zipPath->empty()) {
        ALOGE("no zip file path was found in bugreportz progress data");
        return false;
    }
    return true;
}

// Removes bugreport
void cleanupFile(const char *path) {
    if (unlink(path) != 0) {
        ALOGE("Could not unlink %s (%s)", path, strerror(errno));
    }
}

void SendBroadcast(const std::string& bugreportPath) {
    std::vector<std::string> am = {
        "/system/bin/cmd", "activity", "broadcast", "--user", "all",
        "--receiver-foreground", "--receiver-include-background",
        "-a", "com.memfault.intent.action.BUGREPORT_FINISHED",
        "--es", "com.memfault.intent.extra.BUGREPORT_PATH", bugreportPath,
        "-n", TARGET_COMPONENT
    };

    RunCommandToFd(STDOUT_FILENO, "", am,
               CommandOptions::WithTimeout(20)
                   .Log("Sending broadcast: '%s'\n")
                   .Always()
                   .RedirectStderr()
                   .Build());
}

uint32_t GetTargetUid(void) {
  std::vector<std::string> cmd = {
      "/system/bin/pm", "list", "packages", "-U", TARGET_APP_ID
  };
  TemporaryFile tempFile;
  RunCommandToFd(tempFile.fd, "", cmd,
             CommandOptions::WithTimeout(20)
                 .Log("Getting " TARGET_APP_ID " UID: '%s'\n")
                 .Always()
                 .Build());
  std::string pmOutput;
  android::base::ReadFileToString(tempFile.path, &pmOutput);
  std::size_t found = pmOutput.rfind(":");
  if (found == std::string::npos) {
    ALOGE("Failed to find UID in %s\n", pmOutput.c_str());
    return 0;
  }
  return std::stoi(pmOutput.substr(found + 1));
}

void CleanupDumpstateLogs(void) {
    glob_t glob_result;
    static constexpr char glob_pattern[] = "/bugreports/*-dumpstate_log-*.txt";
    const int ret = glob(glob_pattern, GLOB_MARK, nullptr, &glob_result);
    if (ret != 0 && ret != GLOB_NOMATCH) {
        globfree(&glob_result);
        return;
    }

    for (size_t i = 0; i < glob_result.gl_pathc; i++) {
      cleanupFile(glob_result.gl_pathv[i]);
    }
    globfree(&glob_result);
}

// Based on https://cs.android.com/android/_/android/platform/frameworks/native/+/0f58ab624b96f7f2e49a606ee8ce0cedeb10004d:cmds/dumpstate/dumpstate.cpp;l=3650;drc=f2a15e8742cf33e37e6c96f4731c298c6749bb68
bool createParentDirs(uint32_t targetUid) {
    char path[sizeof(TARGET_DIR)];
    memcpy(path, TARGET_DIR, sizeof(TARGET_DIR));

    char *chp = const_cast<char *> (path);
    bool success = true;

    /* skip initial slash */
    if (chp[0] == '/') {
        chp++;
    }

    /* create leading directories, if necessary */
    struct stat dir_stat;
    while (chp && chp[0]) {
        chp = strchr(chp, '/');
        if (chp) {
            *chp = 0;
            if (stat(path, &dir_stat) == -1 || !S_ISDIR(dir_stat.st_mode)) {
                ALOGD("Creating directory %s\n", path);
                if (mkdir(path, 0770)) { /* drwxrwx--- */
                    ALOGE("Unable to create directory %s: %s\n", path, strerror(errno));
                    success = false;
                } else if (chown(path, targetUid, targetUid)) {
                    ALOGE("Unable to change ownership of dir %s: %s\n", path, strerror(errno));
                    success = false;
                }
            }
            *chp++ = '/';
        }
    }

    return success;
}


}  // namespace

int main(void) {
    const uint32_t targetUid = GetTargetUid();
    ALOGI("Target UID: %d", targetUid);

    if (!createParentDirs(targetUid)) {
        ALOGE("Failed to create output directories. ");
        return EXIT_FAILURE;
    }

    ALOGI("Starting bugreport collecting service");

    auto t0 = std::chrono::steady_clock::now();

    // Start the dumpstatez service.
    android::base::SetProperty("ctl.start", "memfault_dumpstatez");

    size_t bytesWritten = 0;

    std::string zipPath;
    bool ret_val = doBugreport(&bytesWritten, &zipPath);

    auto delta = std::chrono::duration_cast<std::chrono::duration<double>>(
                     std::chrono::steady_clock::now() - t0)
                     .count();

    std::string result = ret_val ? "success" : "failed";
    ALOGI("bugreport %s in %.02fs, %zu bytes written", result.c_str(), delta, bytesWritten);

    if (!ret_val) {
      return EXIT_FAILURE;
    }

    ALOGI("Bugreport captured %s", zipPath.c_str());
    std::string targetPath = TARGET_DIR + android::base::Basename(zipPath);

    ALOGI("Coping bugreport to %s", targetPath.c_str());
    if (!copyFile(zipPath, targetPath)) {
        ALOGE("Unable to copy bugreport: %s\n", strerror(errno));
        goto cleanup;
    }

    if (chown(targetPath.c_str(), targetUid, targetUid)) {
        ALOGE("Unable to change ownership to system of %s: %s\n",
              targetPath.c_str(), strerror(errno));
        goto cleanup;
    }

    SendBroadcast(targetPath);
    CleanupDumpstateLogs();

cleanup:
    cleanupFile(zipPath.c_str());

    // No matter how doBugreport() finished, let's try to explicitly stop
    // dumpstatez in case it stalled.
    android::base::SetProperty("ctl.stop", "memfault_dumpstatez");

    return EXIT_SUCCESS;
}
