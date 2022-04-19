#!/usr/bin/env python3
# -*- coding: utf-8 -*
import abc
import argparse  # requires Python 3.2+
import datetime
import glob
import logging
import logging.handlers
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
from typing import Any, Iterable, List, Optional, Tuple

LOG_FILE = "validate-sdk-integration.log"

logging.basicConfig(format="%(message)s", level=logging.INFO)

DEFAULT_ENCODING = "utf-8"
PLACEHOLDER_BORT_AOSP_PATCH_VERSION = "manually_patched"
PLACEHOLDER_BORT_APP_ID = "vnd.myandroid.bortappid"
PLACEHOLDER_BORT_OTA_APP_ID = "vnd.myandroid.bort.otaappid"
PLACEHOLDER_FEATURE_NAME = "vnd.myandroid.bortfeaturename"
RELEASES = range(8, 12 + 1)
SCRIPT_DIR = os.path.dirname(os.path.realpath(__file__))
GRADLE_PROPERTIES = os.path.join(SCRIPT_DIR, "MemfaultPackages", "gradle.properties")
PYTHON_MIN_VERSION = ("3", "6", "0")
USAGE_REPORTER_APPLICATION_ID = "com.memfault.usagereporter"
USAGE_REPORTER_APK_PATH = (
    r"package:/system/priv-app/MemfaultUsageReporter/MemfaultUsageReporter.apk"
)
MEMFAULT_DUMPSTATE_RUNNER_PATH = "/system/bin/MemfaultDumpstateRunner"
MEMFAULT_INIT_RC_PATH = "/etc/init/memfault_init.rc"
MEMFAULT_DUMPSTER_PATH = "/system/bin/MemfaultDumpster"
MEMFAULT_DUMPSTER_RC_PATH = "/etc/init/memfault_dumpster.rc"
MEMFAULT_STRUCTURED_RC_PATH = "/etc/init/memfault_structured_logd.rc"
MEMFAULT_STRUCTURED_DATA_PATH = "/data/system/MemfaultStructuredLogd/"
MEMFAULT_STRUCTURED_EXEC_PATH = "/system/bin/MemfaultStructuredLogd"
BORT_APK_PATH = r"package:/system/priv-app/MemfaultBort/MemfaultBort.apk"
VENDOR_CIL_PATH = "/vendor/etc/selinux/vendor_sepolicy.cil"
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


def _get_bort_version():
    properties = {}
    with open(GRADLE_PROPERTIES) as properties_file:
        for line in properties_file:
            matches = re.match(r"^([A-Z_]+)=(.*)\s+$", line)
            if matches:
                properties[matches.group(1)] = matches.group(2)
    return "%s.%s.%s" % (
        properties["UPSTREAM_MAJOR_VERSION"],
        properties["UPSTREAM_MINOR_VERSION"],
        properties["UPSTREAM_PATCH_VERSION"],
    )


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
        self._exclude_dirs = exclude or []
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

        mapping = {
            PLACEHOLDER_BORT_AOSP_PATCH_VERSION: _get_bort_version(),
        }

        for patch_abspath in glob.iglob(glob_pattern, recursive=True):
            patch_relpath = os.path.relpath(patch_abspath, self._patches_dir)
            repo_subdir = os.path.dirname(patch_relpath)

            if repo_subdir in self._exclude_dirs:
                logging.info("Skipping patch: %r: excluded!", patch_relpath)
                continue

            with open(patch_abspath) as patch_file:
                content = _replace_placeholders(patch_file.read(), mapping)

            if not self._force and self._check_patch(repo_subdir, patch_relpath, content):
                continue  # Already applied!

            self._apply_patch(repo_subdir, patch_relpath, content)

    def _check_patch(self, repo_subdir, patch_relpath, content) -> bool:
        check_cmd = self._check_patch_command
        try:
            subprocess.check_output(
                check_cmd,
                cwd=os.path.join(self._aosp_root, repo_subdir),
                input=content.encode(DEFAULT_ENCODING),
            )
            logging.info("Skipping patch %r: already applied!", patch_relpath)
            return True

        except (subprocess.CalledProcessError, FileNotFoundError):
            return False

    def _apply_patch(self, repo_subdir, patch_relpath, content):
        apply_cmd = self._apply_patch_command
        logging.info("Running %r (in %r)", shlex_join(apply_cmd), repo_subdir)

        try:
            output = subprocess.check_output(
                apply_cmd,
                cwd=os.path.join(self._aosp_root, repo_subdir),
                input=content.encode(DEFAULT_ENCODING),
                stderr=sys.stderr,
            )
            logging.info(output.decode(DEFAULT_ENCODING))

        except (subprocess.CalledProcessError, FileNotFoundError):
            if repo_subdir == "device/google/cuttlefish":
                logging.exception(
                    "Failed to apply %r (only needed for Cuttlefish/AVD)", patch_relpath
                )
                self._warnings.append(patch_relpath)
            else:
                logging.exception("Failed to apply %r", patch_relpath)
                self._errors.append(patch_relpath)


def _replace_placeholders_in_file(file_abspath, mapping):
    logging.info("Patching %r with %r", file_abspath, mapping)

    with open(file_abspath) as file:
        content = _replace_placeholders(file.read(), mapping)

    with open(file_abspath, "w") as file:
        file.write(content)


class PatchBortCommand(Command):
    def __init__(self, path, bort_app_id, bort_ota_app_id=None, vendor_feature_name=None):
        self._path = path
        self._bort_app_id = bort_app_id
        self._bort_ota_app_id = bort_ota_app_id
        self._vendor_feature_name = vendor_feature_name or bort_app_id

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "patch-bort")
        parser.add_argument("--bort-app-id", type=android_application_id_type, required=True)
        parser.add_argument("--bort-ota-app-id", type=android_application_id_type, required=False)
        parser.add_argument(
            "--vendor-feature-name", type=str, help="Defaults to the provided Application ID"
        )
        parser.add_argument(
            "path",
            type=readable_dir_type,
            help="The path to the MemfaultPackages folder from the SDK",
        )

    def run(self):
        for file_relpath in [
            "bort.properties",
        ]:
            replacements = {
                PLACEHOLDER_BORT_APP_ID: self._bort_app_id,
                PLACEHOLDER_FEATURE_NAME: self._vendor_feature_name,
            }

            if self._bort_ota_app_id:
                replacements[PLACEHOLDER_BORT_OTA_APP_ID] = self._bort_ota_app_id

            file_abspath = os.path.join(self._path, file_relpath)
            _replace_placeholders_in_file(
                file_abspath,
                mapping=replacements,
            )


def _get_shell_cmd_output_and_errors(
    *, description: str, cmd: Tuple
) -> Tuple[Optional[str], List[str]]:
    logging.info("\n%s", description)
    shell_cmd = shlex_join(cmd)
    logging.info("\t%s", shell_cmd)

    def _shell_command():
        try:
            return shell_command_type(shell_cmd)
        except argparse.ArgumentTypeError as arg_error:
            sys.exit(str(arg_error))

    try:
        # In case the host system is Windows, adb will used \r\n as line endings, and this breaks our regexes, so
        # configure universal newlines.
        output = subprocess.check_output(
            _shell_command(), stderr=sys.stderr, encoding="utf-8", universal_newlines=True
        )
        result: str = output[:-1]  # Trim trailing newline
        return result, []
    except subprocess.CalledProcessError as error:
        return None, [str(error)]


def _create_adb_command(cmd: Tuple, device: Optional[str] = None) -> Tuple:
    return ("adb", *(("-s", device) if device else ()), *cmd)


def _get_adb_shell_cmd_output_and_errors(
    *, description: str, cmd, device: Optional[str] = None
) -> Tuple[Optional[str], List[str]]:
    return _get_shell_cmd_output_and_errors(
        description=description, cmd=_create_adb_command(("shell", *cmd), device=device)
    )


class _Matcher(abc.ABC):
    @abc.abstractmethod
    def __call__(self, adb_output: str) -> Tuple[bool, str]:
        pass


class _RegexMatcher(_Matcher):
    def __init__(self, pattern: str) -> None:
        self._re = re.compile(pattern, flags=re.RegexFlag.MULTILINE)

    def __call__(self, adb_output: str) -> Tuple[bool, str]:
        return bool(self._re.search(adb_output)), f"Expected pattern: {self._re.pattern}"


class _IdleWhitelistMatcher(_Matcher):
    def __init__(self, bort_app_id: str) -> None:
        self._bort_app_id = bort_app_id

    def __call__(self, output: str) -> Tuple[bool, str]:
        lines = output.splitlines()
        for idx, line in enumerate(lines):
            if "Whitelist system apps:" in line:
                whitelist_start = idx
                break
        else:
            return False, "Failed to find 'Whitelist system apps' in output"
        for line in lines[whitelist_start:]:
            if self._bort_app_id in line:
                return True, f"Found '{self._bort_app_id}'"
        return False, f"Failed to find '{self._bort_app_id}' in 'Whitelist system apps' list"


class _ConnectedDeviceMatcher(_Matcher):
    def __init__(self, device: Optional[str]) -> None:
        self._device = device

    def __call__(self, output: str) -> Tuple[bool, str]:
        lines = output.splitlines()
        if len(lines) < 2:
            return False, "Too few output lines from 'adb devices'"
        if "List of devices attached" not in lines[0]:
            return False, "Unexpected output from 'adb devices'"
        # If a device was provided, verify it's connected
        if self._device:
            for line in lines[1:]:
                if self._device in line:
                    return True, f"Found '{self._device}'"
            return False, f"Failed to find '{self._device}'"

        # Otherwise, verify any device is connected
        # Entries look like 'XXX device'
        for line in lines[1:]:
            if "device" in line:
                return True, "Found a device"
        return False, "No connected devices"


class _AlwaysMatcher(_Matcher):
    def __call__(self, output: str) -> Tuple[bool, str]:
        return True, "OK"


def _format_error(description: str, *details: Any) -> str:
    return (2 * os.linesep).join(map(str, ("", description, *details)))


def _expect_or_errors(*, output: Optional[str], description: str, matcher: _Matcher) -> List[str]:
    if output is None:
        return [_format_error(description, "No output to match")]
    passed, reason = matcher(output)
    if not passed:
        logging.info("\t Test failed")
        return [_format_error(description, "Output did not match:", output, reason)]

    logging.info("\tTest passed")
    return []


def _run_shell_cmd_and_expect(*, description: str, cmd: Tuple, matcher: _Matcher) -> List[str]:
    output, errors = _get_shell_cmd_output_and_errors(description=description, cmd=cmd)
    if errors:
        return errors
    return _expect_or_errors(output=output, description=description, matcher=matcher)


def _run_adb_shell_cmd_and_expect(
    *, description: str, cmd: Tuple, matcher: _Matcher, device: Optional[str] = None
) -> List[str]:
    output, errors = _get_adb_shell_cmd_output_and_errors(
        description=description, cmd=cmd, device=device
    )
    if errors:
        return errors
    return _expect_or_errors(output=output, description=description, matcher=matcher)


def _run_adb_shell_dumpsys_package(
    package_id: str, device: Optional[str] = None
) -> Tuple[Optional[str], List[str]]:
    output, errors = _get_adb_shell_cmd_output_and_errors(
        description=f"Querying package info for {package_id}",
        cmd=("dumpsys", "package", package_id),
        device=device,
    )

    unable_to_find_str = f"Unable to find package: {package_id}"
    if output and unable_to_find_str in output:
        return None, [_format_error(unable_to_find_str, output)]

    return output, errors


def _check_file_ownership_and_secontext(
    *,
    path: str,
    mode: str,
    owner: str,
    group: str,
    secontext: str,
    device: Optional[str] = None,
    directory: bool = False,
) -> List[str]:
    return _run_adb_shell_cmd_and_expect(
        description=f"Verifying {path} is installed correctly",
        cmd=("ls", "-lZd" if directory else "-lZ", path),
        matcher=_RegexMatcher(rf"^{mode}\s[0-9]+\s{owner}\s{group}\s{secontext}.*{path}$"),
        device=device,
    )


def _log_errors(errors: Iterable[str]) -> None:
    for error in errors:
        logging.info(LOG_ENTRY_SEPARATOR)
        logging.info(error)


def _verify_device_connected(device: Optional[str] = None) -> List[str]:
    """
    If a target device is specified, verify that specific is connected. Otherwise, verify any device is connected.
    If there are multiple devices, `-s` (thus `--device`) must be used as well.
    """

    return _run_shell_cmd_and_expect(
        description="Verifying device is connected",
        cmd=("adb", "devices"),
        matcher=_ConnectedDeviceMatcher(device),
    )


def _check_bort_app_id(bort_app_id: str) -> None:
    if bort_app_id == PLACEHOLDER_BORT_APP_ID:
        sys.exit(
            f"Invalid application ID '{bort_app_id}'. Please configure BORT_APPLICATION_ID in bort.properties."
        )


def _check_feature_name(feature_name: str) -> None:
    if feature_name == PLACEHOLDER_FEATURE_NAME:
        sys.exit(
            f"Invalid feature name '{feature_name}'. Please configure BORT_FEATURE_NAME in bort.properties."
        )


def _send_broadcast(
    bort_app_id: str, description: str, broadcast: Tuple, device: Optional[str] = None
):
    _check_bort_app_id(bort_app_id)
    output, errors = _get_adb_shell_cmd_output_and_errors(
        description=description, cmd=broadcast, device=device
    )
    logging.info(output)
    if errors:
        _log_errors(errors)
        sys.exit(" Failure: unable to send broadcast")
    logging.info("Sent broadcast: check logcat logs for result")


class RequestBugReport(Command):
    def __init__(self, bort_app_id: str, device: Optional[str] = None):
        self._bort_app_id = bort_app_id
        self._device = device

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "request-bug-report")
        parser.add_argument("--bort-app-id", type=android_application_id_type, required=True)
        parser.add_argument(
            "--device",
            type=str,
            help="Optional device ID passed to ADB's `-s` flag. Required if multiple devices are connected.",
        )

    def run(self):
        _check_bort_app_id(self._bort_app_id)
        broadcast_cmd = (
            "am",
            "broadcast",
            "--receiver-include-background",
            "-a",
            "com.memfault.intent.action.REQUEST_BUG_REPORT",
            "-n",
            f"{self._bort_app_id}/com.memfault.bort.receivers.ShellControlReceiver",
        )
        _send_broadcast(
            self._bort_app_id, "Requesting bug report from bort", broadcast_cmd, self._device
        )


class EnableBort(Command):
    def __init__(self, bort_app_id, device=None):
        self._bort_app_id = bort_app_id
        self._device = device

    @classmethod
    def register(cls, create_parser):
        parser = create_parser(cls, "enable-bort")
        parser.add_argument("--bort-app-id", type=android_application_id_type, required=True)
        parser.add_argument(
            "--device",
            type=str,
            help="Optional device ID passed to ADB's `-s` flag. Required if multiple devices are connected.",
        )

    def run(self):
        _check_bort_app_id(self._bort_app_id)
        broadcast_cmd = (
            "am",
            "broadcast",
            "--receiver-include-background",
            "-a",
            "com.memfault.intent.action.BORT_ENABLE",
            "-n",
            f"{self._bort_app_id}/com.memfault.bort.receivers.ShellControlReceiver",
            "--ez",
            "com.memfault.intent.extra.BORT_ENABLED",
            "true",
        )
        _send_broadcast(self._bort_app_id, "Enabling bort", broadcast_cmd, self._device)


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

    def _getprop(self, key: str) -> Optional[str]:
        output, errors = _get_adb_shell_cmd_output_and_errors(
            description=f"Querying {key}", cmd=("getprop", key), device=self._device
        )
        if errors:
            self._errors.extend(errors)
        return output

    def _query_sdk_version(self) -> Optional[int]:
        version_str = self._getprop("ro.build.version.sdk")
        if version_str:
            return int(version_str)
        return None

    def _query_build_type(self) -> Optional[str]:
        return self._getprop("ro.build.type")

    def _check_vendor_sepolicy_cil(self):
        with tempfile.NamedTemporaryFile() as vendor_cil:
            _, errors = _get_shell_cmd_output_and_errors(
                description="Verifying selinux access rules",
                cmd=_create_adb_command(
                    ("pull", VENDOR_CIL_PATH, vendor_cil.name), device=self._device
                ),
            )
            if not errors:
                with open(vendor_cil.name, "r") as cil:
                    rules = cil.read()
                    if not re.search(
                        r"allow .*_app_.* memfault_dumpster_service \(service_manager \(find\)\)",
                        rules,
                    ):
                        errors.extend(
                            [
                                "Expected a selinux rule (allow priv_app memfault_dumpster_service:service_manager find), please recheck integration"
                            ]
                        )

            return errors

    def _run_checks_requiring_root(self, sdk_version: int):
        _run_shell_cmd_and_expect(
            description="Restarting ADB with root permissions",
            cmd=_create_adb_command(("root",), device=self._device),
            matcher=_AlwaysMatcher(),
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_DUMPSTATE_RUNNER_PATH,
                mode="-rwxr-xr-x",
                owner="root",
                group="shell",
                secontext="u:object_r:dumpstate_exec:s0",
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_INIT_RC_PATH,
                mode="-rw-r--r--",
                owner="root",
                group="root",
                secontext="u:object_r:system_file:s0",
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_DUMPSTER_PATH,
                mode="-rwxr-xr-x",
                owner="root",
                group="shell",
                secontext="u:object_r:dumpstate_exec:s0",
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_DUMPSTER_RC_PATH,
                mode="-rw-r--r--",
                owner="root",
                group="root",
                secontext="u:object_r:system_file:s0",
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=f"/data/data/{self._bort_app_id}/",
                mode="drwx------",
                owner="u[0-9]+_a[0-9]+",
                group="u[0-9]+_a[0-9]+",
                secontext="u:object_r:bort_app_data_file:s0",
                directory=True,
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_STRUCTURED_RC_PATH,
                mode="-rw-r--r--",
                owner="root",
                group="root",
                secontext="u:object_r:system_file:s0",
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_STRUCTURED_EXEC_PATH,
                mode="-rwxr-xr-x",
                owner="root",
                group="shell",
                secontext="u:object_r:memfault_structured_exec:s0",
                directory=True,
                device=self._device,
            )
        )

        self._errors.extend(
            _check_file_ownership_and_secontext(
                path=MEMFAULT_STRUCTURED_DATA_PATH,
                mode="drwx------",
                owner="system",
                group="system",
                secontext="u:object_r:memfault_structured_data_file:s0",
                directory=True,
                device=self._device,
            )
        )

        if sdk_version >= 28:
            self._errors.extend(self._check_vendor_sepolicy_cil())

    def _check_bort_permissions(self, bort_package_info: Optional[str], sdk_version: int):
        for permission, min_sdk_version in (
            ("android.permission.FOREGROUND_SERVICE", 28),
            ("android.permission.RECEIVE_BOOT_COMPLETED", 1),
            ("android.permission.INTERNET", 1),
            ("android.permission.ACCESS_NETWORK_STATE", 1),
            ("android.permission.DUMP", 1),
            ("android.permission.WAKE_LOCK", 1),
        ):
            if sdk_version < min_sdk_version:
                logging.info(
                    "\nSkipping check for '%s' because it is not supported (SDK version %d < %d)",
                    permission,
                    sdk_version,
                    min_sdk_version,
                )
                continue
            description = f"Verifying MemfaultBort app has permission '{permission}'"
            logging.info("\n%s", description)
            self._errors.extend(
                _expect_or_errors(
                    output=bort_package_info,
                    description=description,
                    matcher=_RegexMatcher(rf"{permission}: granted=true"),
                )
            )

    def _check_package_versions(self, *package_infos: Optional[str]):
        description = "\nVerifying Bort packages have the same version"
        logging.info(description)
        if not all(package_infos):
            logging.info("\t Test failed")
            self._errors.append(_format_error(description, "Missing package info"))
            return

        def _find_version_names(info: str):
            version_names = re.findall(r"versionName=(\S+)", info, re.RegexFlag.MULTILINE)
            if len(version_names) > 1:
                self._errors.append(
                    _format_error(description, "Multiple versions of same package found:", info)
                )
            return version_names[0]

        versions = set(_find_version_names(info) for info in package_infos if info)
        if len(versions) > 1:
            self._errors.append(
                _format_error(description, "Different versions found:", *package_infos)
            )

        logging.info("\tTest passed")

    def run(self):
        should_rollover = os.path.exists(LOG_FILE) and os.path.getsize(LOG_FILE) > 0
        fh = logging.handlers.RotatingFileHandler(LOG_FILE, backupCount=5)
        if should_rollover:
            fh.doRollover()
        fh.setLevel(logging.DEBUG)
        logging.getLogger("").addHandler(fh)

        _check_bort_app_id(self._bort_app_id)
        _check_feature_name(self._vendor_feature_name)
        logging.info(LOG_ENTRY_SEPARATOR)
        logging.info("validate-sdk-integration %s", datetime.datetime.now())
        errors = _verify_device_connected(self._device)
        if errors:
            _log_errors(errors)
            sys.exit("Failure: device not found. No tests run.")

        sdk_version = self._query_sdk_version()
        if not sdk_version:
            sys.exit("Failure: could not get SDK version.")

        build_type = self._query_build_type()
        if build_type == "user":
            logging.info(
                "'%s' build detected. Skipping validation checks that require adb root!", build_type
            )
        else:
            self._run_checks_requiring_root(sdk_version)

        self._errors.extend(
            _run_adb_shell_cmd_and_expect(
                description="Verifying MemfaultUsageReporter app is installed",
                cmd=("pm", "path", USAGE_REPORTER_APPLICATION_ID),
                matcher=_RegexMatcher(USAGE_REPORTER_APK_PATH),
                device=self._device,
            )
        )
        self._errors.extend(
            _run_adb_shell_cmd_and_expect(
                description="Verifying MemfaultBort app is installed",
                cmd=("pm", "path", self._bort_app_id),
                matcher=_RegexMatcher(BORT_APK_PATH),
                device=self._device,
            )
        )
        self._errors.extend(
            _run_adb_shell_cmd_and_expect(
                description=f"Verifying device has feature {self._vendor_feature_name}",
                cmd=("pm", "list", "features"),
                matcher=_RegexMatcher(rf"^feature\:{self._vendor_feature_name}$"),
                device=self._device,
            )
        )

        def _get_package_info(app_id: str) -> Optional[str]:
            package_info, errors = _run_adb_shell_dumpsys_package(app_id, device=self._device)
            self._errors.extend(errors)
            return package_info

        bort_package_info, usage_reporter_package_info = map(
            _get_package_info, (self._bort_app_id, USAGE_REPORTER_APPLICATION_ID)
        )

        self._check_bort_permissions(bort_package_info, sdk_version)
        self._check_package_versions(bort_package_info, usage_reporter_package_info)

        self._errors.extend(
            _run_adb_shell_cmd_and_expect(
                description="Verifying MemfaultBort is on device idle whitelist",
                cmd=("dumpsys", "deviceidle"),
                matcher=_IdleWhitelistMatcher(self._bort_app_id),
                device=self._device,
            )
        )

        if self._errors:
            for error in self._errors:
                logging.info(LOG_ENTRY_SEPARATOR)
                logging.info(error)
            sys.exit(f" Failure: One or more errors detected. See {LOG_FILE} for details")

        logging.info("")
        logging.info("SUCCESS: Bort SDK on the connected device appears to be valid")
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
        ValidateConnectedDevice.register(create_parser)
        RequestBugReport.register(create_parser)
        EnableBort.register(create_parser)

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
