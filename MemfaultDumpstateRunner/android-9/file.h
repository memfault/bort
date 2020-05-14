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

#pragma once

#if PLATFORM_SDK_VERSION <= 28

#include <sys/stat.h>
#include <sys/types.h>

#include <string>

#include <android-base/unique_fd.h>

#if defined(__APPLE__)
/** Mac OS has always had a 64-bit off_t, so it doesn't have off64_t. */
typedef off_t off64_t;
#endif

#if !defined(_WIN32) && !defined(O_BINARY)
/** Windows needs O_BINARY, but Unix never mangles line endings. */
#define O_BINARY 0
#endif

#if defined(_WIN32) && !defined(O_CLOEXEC)
/** Windows has O_CLOEXEC but calls it O_NOINHERIT for some reason. */
#define O_CLOEXEC O_NOINHERIT
#endif

class TemporaryFile {
public:
  TemporaryFile();
  explicit TemporaryFile(const std::string& tmp_dir);
  ~TemporaryFile();

  // Release the ownership of fd, caller is reponsible for closing the
  // fd or stream properly.
  int release();
  // Don't remove the temporary file in the destructor.
  void DoNotRemove() { remove_file_ = false; }

  int fd;
  char path[1024];

private:
  void init(const std::string& tmp_dir);

  bool remove_file_ = true;

  TemporaryFile(const TemporaryFile&) = delete;
  void operator=(const TemporaryFile&) = delete;
};

#endif
