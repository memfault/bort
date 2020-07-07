#!/usr/bin/env python3
import abc
import argparse  # requires Python 3.2+
import contextlib
import datetime
import glob
import io
import logging
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys

LOG_FILE = "validate-sdk-integration.log"

logging.basicConfig(format="%(message)s", level=logging.INFO)
fh = logging.FileHandler(LOG_FILE)
fh.setLevel(logging.DEBUG)
logging.getLogger("").addHandler(fh)

PLACEHOLDER_BORT_APP_ID = "vnd.myandroid.bortappid"
PLACEHOLDER_FEATURE_NAME = "vnd.myandroid.bortfeaturename"
RELEASES = range(9, 10 + 1)
SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))
PYTHON_MIN_VERSION = ("3", "6", "0")
USAGE_REPORTER_APPLICATION_ID = "com.memfault.usagereporter"
USAGE_REPORTER_APK_PATH = (
    r"package:/system/priv-app/MemfaultUsageReporter/MemfaultUsageReporter.apk"
)
BORT_APK_PATH = r"package:/system/priv-app/MemfaultBort/MemfaultBort.apk"
LOG_ENTRY_SEPARATOR = "============================================================"


def shlex_join(cmd):
    """
    Backport of shlex.join (which would require Python 3.8+)
    """
    return " ".join(cmd)


def readable_dir_type(path):
    """
    Arg parser for directory paths
    """
    if os.path.isdir(path) and os.access(path, os.R_OK):
        return path

    raise argparse.ArgumentTypeError("Couldn't find/access directory %r" % path)


def shell_command_type(arg):
    """
    Argument type for shell commands
    """
    parts = shlex.split(arg)

    if not parts:
        raise argparse.ArgumentTypeError("%s is not a valid command" % arg)

    cmd = parts[0]
    if not shutil.which(cmd):
        raise argparse.ArgumentTypeError("Couldn't find executable %s" % cmd)

    return parts


def android_application_id_type(arg):
    """
    Argument type for Android Application ID
    """
    if not re.match(r"^([a-z][a-z0-9]+)(\.[a-z][a-z0-9]+)+$", arg, re.RegexFlag.IGNORECASE):
        raise argparse.ArgumentTypeError("Not a valid application ID: %r" % arg)

    return arg


def _replace_placeholders(content, mapping):
    for placeholder, value in mapping.items():
        content = content.replace(placeholder, value)

    return content


class Command(abc.ABC):
    """
    Base class for commands
    """

    @abc.abstractmethod
    def register(self, create_parser):
        pass

    @abc.abstractmethod
    def run(self):
        pass


class PatchAOSPCommand(Command):
    def __init__(
        self, aosp_root, check_patch_command, apply_patch_command, force, android_release, exclude
    ):
        self._aosp_root = aosp_root
        self._check_patch_command = check_patch_command or self._default_check_patch_command()
        self._apply_patch_command = apply_patch_command or self._default_apply_patch_command()
        self._force = force
        self._patches_dir = os.path.join(SCRIPT_DIR, "patches", f"android-{android_release}")
        self._exclude_dirs = exclude
        self._errors = []
        self._warnings = []

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "patch-aosp")
        parser.add_argument(
            "aosp_root", type=readable_dir_type, help="The path of the checked out AOSP repository"
        )
        parser.add_argument(
            "--android-release",
            type=int,
            choices=RELEASES,
            required=True,
            help="Android platform version",
        )
        parser.add_argument(
            "--check-patch-command",
            type=shell_command_type,
            default=None,
            help="Command to check whether patch is applied. Expected to exit with status 0 if patch is applied.",
        )
        parser.add_argument(
            "--apply-patch-command",
            type=shell_command_type,
            default=None,
            help="Command to apply patch. The patch is provided through stdin.",
        )
        parser.add_argument(
            "--force",
            action="store_true",
            default=False,
            help="Apply patch, even when already applied.",
        )
        parser.add_argument(
            "--exclude", action="append", help="Directories to exclude from patching"
        )

    @staticmethod
    def _default_apply_patch_command():
        try:
            return shell_command_type("patch -f -p1")
        except argparse.ArgumentTypeError as error:
            sys.exit(str(error))

    @staticmethod
    def _default_check_patch_command():
        try:
            return shell_command_type("patch -R -p1 --dry-run")
        except argparse.ArgumentTypeError as error:
            sys.exit(str(error))

    def run(self):
        self._apply_all_patches()

        if self._errors:
            sys.exit("Some patches couldn't be applied.")

        if self._warnings:
            sys.exit("Some optional patches couldn't be applied.")

        logging.info("All patches applied successfully.")

    def _apply_all_patches(self):
        glob_pattern = os.path.join(self._patches_dir, "**", "git.diff")

        for patch_abspath in glob.iglob(glob_pattern, recursive=True):
            patch_relpath = os.path.relpath(patch_abspath, self._patches_dir)
            repo_subdir = os.path.dirname(patch_relpath)

            if repo_subdir in self._exclude_dirs:
                logging.info("Skipping patch: %r: excluded!", patch_relpath)
                continue

            with open(patch_abspath) as patch_file:
                content = patch_file.read()

            if not self._force and self._check_patch(repo_subdir, patch_relpath, content):
                continue  # Already applied!

            self._apply_patch(repo_subdir, patch_relpath, content)

    def _check_patch(self, repo_subdir, patch_relpath, content) -> bool:
        check_cmd = self._check_patch_command
        try:
            subprocess.check_output(
                check_cmd,
                cwd=os.path.join(self._aosp_root, repo_subdir),
                input=content.encode("utf-8"),
            )
            logging.info("Skipping patch %r: already applied!", patch_relpath)
            return True

        except subprocess.CalledProcessError:
            return False

    def _apply_patch(self, repo_subdir, patch_relpath, content):
        apply_cmd = self._apply_patch_command
        logging.info("Running %r (in %r)", shlex_join(apply_cmd), repo_subdir)

        try:
            output = subprocess.check_output(
                apply_cmd,
                cwd=os.path.join(self._aosp_root, repo_subdir),
                input=content.encode("utf-8"),
                stderr=sys.stderr,
            )
            logging.info(output.decode("utf-8"))

        except (subprocess.CalledProcessError, FileNotFoundError) as error:
            logging.warning(error)
            if repo_subdir == "device/google/cuttlefish":
                logging.warning(
                    "Failed to apply %r (only needed for Cuttlefish/AVD)", patch_relpath,
                )
                self._warnings.append(patch_relpath)
            else:
                logging.warning("Failed to apply %r", patch_relpath)
                self._errors.append(patch_relpath)


def _replace_placeholders_in_file(file_abspath, mapping):
    logging.info("Patching %r with %r", file_abspath, mapping)

    with open(file_abspath) as file:
        content = _replace_placeholders(file.read(), mapping)

    with open(file_abspath, "w") as file:
        file.write(content)


class PatchBortCommand(Command):
    def __init__(self, path, bort_app_id, vendor_feature_name=None):
        self._path = path
        self._bort_app_id = bort_app_id
        self._vendor_feature_name = vendor_feature_name or bort_app_id

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "patch-bort")
        parser.add_argument("--bort-app-id", type=android_application_id_type, required=True)
        parser.add_argument(
            "--vendor-feature-name", type=str, help="Defaults to the provided Application ID"
        )
        parser.add_argument(
            "path", type=readable_dir_type, help="The path to the MemfaultBort folder from the SDK"
        )

    def run(self):
        for file_relpath in [
            "app/build.gradle",
            "app/src/main/AndroidManifest.xml",
            "com.memfault.bort.xml",
        ]:
            file_abspath = os.path.join(self._path, file_relpath)
            _replace_placeholders_in_file(
                file_abspath,
                mapping={
                    PLACEHOLDER_BORT_APP_ID: self._bort_app_id,
                    PLACEHOLDER_FEATURE_NAME: self._vendor_feature_name,
                },
            )


class PatchDumpstateRunnerCommand(Command):
    def __init__(self, path, bort_app_id):
        self._path = path
        self._bort_app_id = bort_app_id

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "patch-dumpstate-runner")
        parser.add_argument("--bort-app-id", type=android_application_id_type, required=True)
        parser.add_argument(
            "path", type=readable_dir_type, help="The path to the MemfaultBort folder from the SDK"
        )

    def run(self):
        for file_relpath in [
            "MemfaultDumpstateRunner.cpp",
        ]:
            file_abspath = os.path.join(self._path, file_relpath)
            _replace_placeholders_in_file(
                file_abspath, mapping={PLACEHOLDER_BORT_APP_ID: self._bort_app_id,},
            )


class ValidateConnectedDevice(Command):
    def __init__(self, bort_app_id, device=None, vendor_feature_name=None):
        self._bort_app_id = bort_app_id
        self._device = device
        self._vendor_feature_name = vendor_feature_name or bort_app_id
        self._errors = []

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "validate-sdk-integration")
        parser.add_argument("--bort-app-id", type=android_application_id_type, required=True)
        parser.add_argument(
            "--device", type=str, help="Optional device ID passed to ADB's `-s` flag"
        )
        parser.add_argument(
            "--vendor-feature-name", type=str, help="Defaults to the provided Application ID"
        )

    @property
    def _device_arg(self):
        return ("-s", self._device) if self._device else ()

    def _get_shell_cmd_output(self, *, description: str, cmd) -> str:
        logging.info("\n%s", description)
        shell_cmd = shlex_join(cmd)
        logging.info("\t%s", shell_cmd)

        def _shell_command():
            try:
                return shell_command_type(shell_cmd)
            except argparse.ArgumentTypeError as arg_error:
                sys.exit(str(arg_error))

        try:
            output = subprocess.check_output(_shell_command(), stderr=sys.stderr)
            return output.decode("utf-8")
        except subprocess.CalledProcessError as error:
            self._errors.append(str(error))

    def _run_shell_cmd_and_expect(self, *, description: str, cmd, pattern: str):
        output = self._get_shell_cmd_output(description=description, cmd=cmd)
        if not output or not re.search(pattern, output, re.RegexFlag.MULTILINE):
            self._errors.append(f"\n\n❌ Expected:\n{pattern}\n\nFound:\n{output}")
            logging.info("\t❌ No match for '%s'", pattern)
            return
        logging.info("\tOutput matched '%s'", pattern)

    def _verify_device_connected(self):
        """
        If a target device is specified, verify that specific is connected. Otherwise, verify any device is connected.
        If there are multiple devices, `-s` (thus `--device`) must be used as well.
        """
        pattern = r"(?<=List of devices attached\n)"
        pattern += rf"(.|\n)*{re.escape(self._device)}" if self._device else "(.)+"
        self._run_shell_cmd_and_expect(
            description="Verifying device is connected", cmd=("adb", "devices"), pattern=pattern
        )

    def _run_adb_shell_cmd_and_expect(self, *, description: str, adb_shell_cmd, pattern: str):
        device_arg = ("-s", self._device) if self._device else ()
        self._run_shell_cmd_and_expect(
            description=description,
            cmd=("adb", *device_arg, "shell", *adb_shell_cmd),
            pattern=pattern,
        )

    def run(self):
        if self._bort_app_id == PLACEHOLDER_BORT_APP_ID:
            sys.exit(
                "Invalid application ID. Please run the 'patch-bort' command and change the application ID."
            )
        logging.info(LOG_ENTRY_SEPARATOR)
        logging.info("validate-sdk-integration %s", datetime.datetime.now())
        self._verify_device_connected()
        if self._errors:
            sys.exit("\n".join((*self._errors, "❌ Failure: device not found. No tests run.")))

        self._run_adb_shell_cmd_and_expect(
            description="Verifying MemfaultUsageReporter app is installed",
            adb_shell_cmd=("pm", "path", USAGE_REPORTER_APPLICATION_ID),
            pattern=USAGE_REPORTER_APK_PATH,
        )
        self._run_adb_shell_cmd_and_expect(
            description="Verifying MemfaultBort app is installed",
            adb_shell_cmd=("pm", "path", self._bort_app_id),
            pattern=BORT_APK_PATH,
        )
        self._run_adb_shell_cmd_and_expect(
            description=f"Verifying device has feature {self._vendor_feature_name}",
            adb_shell_cmd=("pm", "list", "features"),
            pattern=rf"^feature\:{self._vendor_feature_name}$",
        )
        for permission in (
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.DUMP",
            "android.permission.WAKE_LOCK",
        ):
            self._run_adb_shell_cmd_and_expect(
                description=f"Verifying MemfaultBort app has permission '{permission}'",
                adb_shell_cmd=("dumpsys", "package", self._bort_app_id),
                pattern=rf"{permission}: granted=true",
            )
        self._run_adb_shell_cmd_and_expect(
            description=f"Verifying MemfaultUsageReport app has permission 'android.permission.DUMP'",
            adb_shell_cmd=("dumpsys", "package", USAGE_REPORTER_APPLICATION_ID),
            pattern=rf"android.permission.DUMP: granted=true",
        )

        self._run_adb_shell_cmd_and_expect(
            description="Verifying MemfaultBort is on device idle whitelist",
            adb_shell_cmd=("dumpsys", "deviceidle"),
            pattern=rf"Whitelist system apps:(.|\n)*({self._bort_app_id})",
        )

        if self._errors:
            for error in self._errors:
                logging.info(LOG_ENTRY_SEPARATOR)
                logging.info(error)
            sys.exit(f"❌ Failure: One or more errors detected. See {LOG_FILE} for details")

        logging.info("\n✅ SUCCESS: Bort SDK on the connected device appears to be valid")
        logging.info("Results written to %s", LOG_FILE)


class CommandLineInterface:
    def __init__(self):
        self._root_parser = argparse.ArgumentParser(
            description="Prepares and validates an AOSP device for Memfault Bort."
        )
        subparsers = self._root_parser.add_subparsers()

        def create_parser(command, *args, **kwargs):
            parser = subparsers.add_parser(*args, **kwargs)
            parser.set_defaults(command=command)
            return parser

        PatchAOSPCommand.register(create_parser)
        PatchBortCommand.register(create_parser)
        PatchDumpstateRunnerCommand.register(create_parser)
        ValidateConnectedDevice.register(create_parser)

    def run(self):
        if platform.python_version_tuple() < PYTHON_MIN_VERSION:
            logging.error(
                "Python %s+ required, found %r",
                ".".join(PYTHON_MIN_VERSION),
                platform.python_version(),
            )
            sys.exit(1)

        args = vars(self._root_parser.parse_args())
        command = args.pop("command", None)

        if not command:
            self._root_parser.print_help()
            sys.exit(1)

        command(**args).run()


if __name__ == "__main__":
    CommandLineInterface().run()
