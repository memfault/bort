#!/usr/bin/env python3

import glob
import os
import subprocess
import sys
from shutil import which

RELEASES = ("9", "10")


def print_usage():
    print(f"Usage: apply-patches.py <aosp_repo_root> <major_release_number>")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print_usage()
        sys.exit(-1)

    aosp_root, release = sys.argv[1:3]
    alternative_cmd = sys.argv[3:] if len(sys.argv) > 3 else None

    if not os.path.isdir(aosp_root):
        print_usage()
        print(f"'{aosp_root}' is not a directory!")
        sys.exit(-2)

    if release not in RELEASES:
        print_usage()
        print(f"'{release}' is not a valid release. Supported: {', '.join(RELEASES)}")
        sys.exit(-3)

    git = which("git")
    if git is None:
        print(f"Requires git!")
        sys.exit(-4)

    script_dir = os.path.dirname(os.path.realpath(__file__))
    patches_dir = os.path.join(script_dir, f"android-{release}")
    glob_pattern = os.path.join(patches_dir, "**", "git.diff")
    has_errors = False
    for patch in glob.glob(glob_pattern, recursive=True):
        repo_dir = os.path.dirname(os.path.relpath(patch, patches_dir))
        cmd = alternative_cmd or ["git", "apply", patch]
        print(f"Running '{' '.join(cmd)}' in {repo_dir}")
        try:
            subprocess.check_call(
                cmd,
                cwd=os.path.join(aosp_root, repo_dir),
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
        except subprocess.CalledProcessError:
            print(f"Failed to apply: {patch}")
            if repo_dir == "device/google/cuttlefish":
                print(
                    "Unless you are interested in building for the Cuttlefish emulator/AVD, you can safely ignore this!"
                )
            has_errors = True
            # Keep going

    if has_errors:
        sys.exit(-5)
