/*
 * Copyright (C) 2015 The Android Open Source Project
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

#if PLATFORM_SDK_VERSION <= 28

#include "file.h"

#include <errno.h>
#include <fcntl.h>
#include <ftw.h>
#include <libgen.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#include <memory>
#include <mutex>
#include <string>
#include <vector>

#if defined(__APPLE__)
#include <mach-o/dyld.h>
#endif
#if defined(_WIN32)
#include <direct.h>
#include <windows.h>
#define O_NOFOLLOW 0
#define OS_PATH_SEPARATOR '\\'
#else
#define OS_PATH_SEPARATOR '/'
#endif

#include "android-base/logging.h"  // and must be after windows.h for ERROR
#include "android-base/macros.h"   // For TEMP_FAILURE_RETRY on Darwin.
#include "android-base/unique_fd.h"
#include "android-base/utf8.h"

#ifdef _WIN32
int mkstemp(char* template_name) {
  if (_mktemp(template_name) == nullptr) {
    return -1;
  }
  // Use open() to match the close() that TemporaryFile's destructor does.
  // Use O_BINARY to match base file APIs.
  return open(template_name, O_CREAT | O_EXCL | O_RDWR | O_BINARY, S_IRUSR | S_IWUSR);
}

char* mkdtemp(char* template_name) {
  if (_mktemp(template_name) == nullptr) {
    return nullptr;
  }
  if (_mkdir(template_name) == -1) {
    return nullptr;
  }
  return template_name;
}
#endif

namespace {

  std::string GetSystemTempDir() {
#if defined(__ANDROID__)
    const auto* tmpdir = getenv("TMPDIR");
  if (tmpdir == nullptr) tmpdir = "/data/local/tmp";
  if (access(tmpdir, R_OK | W_OK | X_OK) == 0) {
    return tmpdir;
  }
  // Tests running in app context can't access /data/local/tmp,
  // so try current directory if /data/local/tmp is not accessible.
  return ".";
#elif defined(_WIN32)
    char tmp_dir[MAX_PATH];
  DWORD result = GetTempPathA(sizeof(tmp_dir), tmp_dir);  // checks TMP env
  CHECK_NE(result, 0ul) << "GetTempPathA failed, error: " << GetLastError();
  CHECK_LT(result, sizeof(tmp_dir)) << "path truncated to: " << result;

  // GetTempPath() returns a path with a trailing slash, but init()
  // does not expect that, so remove it.
  CHECK_EQ(tmp_dir[result - 1], '\\');
  tmp_dir[result - 1] = '\0';
  return tmp_dir;
#else
    const auto* tmpdir = getenv("TMPDIR");
    if (tmpdir == nullptr) tmpdir = "/tmp";
    return tmpdir;
#endif
  }

}  // namespace

TemporaryFile::TemporaryFile() {
  init(GetSystemTempDir());
}

TemporaryFile::TemporaryFile(const std::string& tmp_dir) {
  init(tmp_dir);
}

TemporaryFile::~TemporaryFile() {
  if (fd != -1) {
    close(fd);
  }
  if (remove_file_) {
    unlink(path);
  }
}

int TemporaryFile::release() {
  int result = fd;
  fd = -1;
  return result;
}

void TemporaryFile::init(const std::string& tmp_dir) {
  snprintf(path, sizeof(path), "%s%cTemporaryFile-XXXXXX", tmp_dir.c_str(), OS_PATH_SEPARATOR);
  fd = mkstemp(path);
}

#endif
