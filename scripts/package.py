#!/usr/bin/env python3
"""Package a verified JAR with a native Java 17 runtime using only Python's stdlib."""

import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tarfile
import tempfile
from datetime import datetime, timezone
import xml.etree.ElementTree as ET
import zipfile


POSIX_LAUNCHER = '''#!/bin/sh
set -eu
case "$0" in
  */*) app_dir=${0%/*} ;;
  *) app_dir=. ;;
esac
app_dir=$(CDPATH= cd -- "$app_dir" && pwd)
exec "$app_dir/runtime/bin/java" -Dfile.encoding=UTF-8 -jar "$app_dir/app/line-tracker.jar" "$@"
'''

WINDOWS_LAUNCHER = '''@echo off
"%~dp0runtime\\bin\\java.exe" -Dfile.encoding=UTF-8 -jar "%~dp0app\\line-tracker.jar" %*
exit /b %errorlevel%
'''

PACKAGE_README = '''# Line Tracker CLI package

This archive includes its own Java 17 runtime. It runs only on the operating
system and CPU architecture named in the archive and build-info.json.
No separate Java installation is needed. This package does not contain a GUI.

Extract the complete archive before running it. From the extracted directory:

macOS/Linux:

    ./line-tracker map examples/insert-delete/old.txt examples/insert-delete/new.txt
    ./line-tracker map OLD NEW --advanced --format json --output results.json

Windows PowerShell:

    .\\line-tracker.cmd map examples\\insert-delete\\old.txt examples\\insert-delete\\new.txt
    .\\line-tracker.cmd map OLD NEW --advanced --format json --output results.json

Quote paths containing spaces. The first example prints 1-2 and 3-3.
Unmatched-line diagnostics go to stderr. Exit codes are 0 success, 1 comparison
or output failure, and 2 invalid usage. Advanced mode includes exact split
reconstruction; the default mode is the unchanged-line baseline.

build-info.json records the source commit, dirty state, Java runtime, platform,
architecture and JAR checksum. Dirty means the build included uncommitted work.
The examples are synthetic development fixtures, not benchmark evidence.
Keep runtime/legal with the runtime when redistributing the archive.
macOS packages are unsigned; managed-computer policy may restrict downloaded code.
'''


def checked(command, root):
    result = subprocess.run(
        [str(part) for part in command], cwd=root, capture_output=True, text=True, timeout=180
    )
    if result.returncode:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {command[0]}\n{result.stderr.strip()}"
        )
    return result.stdout.strip()


def sha256(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def verified_jar(root):
    jar = root / "target/line-tracker.jar"
    report = root / "target/failsafe-reports/failsafe-summary.xml"
    if not jar.is_file() or not report.is_file():
        raise RuntimeError("Run the Maven wrapper clean verify before packaging; JAR/report missing.")
    summary = ET.parse(report).getroot()
    if (
        int(summary.findtext("completed", "0")) <= int(summary.findtext("skipped", "0"))
        or int(summary.findtext("errors", "1")) != 0
        or int(summary.findtext("failures", "1")) != 0
        or summary.get("timeout") == "true"
    ):
        raise RuntimeError("Packaged-JAR verification did not pass; run clean verify.")
    if report.stat().st_mtime_ns < jar.stat().st_mtime_ns:
        raise RuntimeError("The JAR is newer than its test report; run clean verify.")
    source_files = [root / "pom.xml", *(path for path in root.glob("src/**/*") if path.is_file())]
    if any(path.stat().st_mtime_ns > jar.stat().st_mtime_ns for path in source_files):
        raise RuntimeError("Java sources or pom.xml changed after packaging; run clean verify.")
    return jar


def read_jdk(root):
    configured = os.environ.get("JAVA_HOME")
    if not configured:
        raise RuntimeError("Set JAVA_HOME to a JDK 17 installation containing jlink and jmods.")
    home = Path(configured).resolve()
    properties = {}
    for line in (home / "release").read_text(encoding="utf-8").splitlines():
        key, separator, value = line.partition("=")
        if separator:
            properties[key] = value.strip('"')
    if not properties.get("JAVA_VERSION", "").startswith("17."):
        raise RuntimeError("Packaging requires JDK 17; JAVA_HOME points to another major version.")
    suffix = ".exe" if os.name == "nt" else ""
    tools = {name: home / "bin" / (name + suffix) for name in ("java", "jlink", "jdeps")}
    if not all(path.is_file() for path in tools.values()) or not (home / "jmods").is_dir():
        raise RuntimeError("JAVA_HOME must contain a full JDK with java, jlink, jdeps and jmods.")
    host = {"Darwin": "macos", "Linux": "linux", "Windows": "windows"}.get(platform.system())
    if host is None:
        raise RuntimeError(f"Unsupported package platform: {platform.system()}")
    architecture = properties.get("OS_ARCH", platform.machine()).lower()
    architecture = {"aarch64": "arm64", "amd64": "x86_64"}.get(architecture, architecture)
    if architecture not in ("arm64", "x86_64"):
        raise RuntimeError(f"Unsupported runtime architecture: {architecture}")
    checked([tools["java"], "--version"], root)
    return home, tools, properties, host, architecture


def write_archive(bundle, archive, windows):
    # Exclusive creation preserves any prior deliverable, even in a concurrent build.
    created = False
    try:
        with archive.open("xb") as output:
            created = True
            if windows:
                with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as packed:
                    for path in sorted(bundle.rglob("*")):
                        packed.write(path, path.relative_to(bundle.parent))
            else:
                with tarfile.open(fileobj=output, mode="w:gz") as packed:
                    packed.add(bundle, arcname=bundle.name)
    except BaseException:
        if created:
            archive.unlink(missing_ok=True)
        raise


def main():
    if sys.version_info < (3, 12):
        raise RuntimeError("Packaging requires Python 3.12 or newer.")
    root = Path(__file__).resolve().parents[1]
    jar = verified_jar(root)
    home, tools, properties, system, architecture = read_jdk(root)
    modules = checked([tools["jdeps"], "--multi-release", "17", "--print-module-deps", jar], root)
    if modules != "java.base":
        raise RuntimeError(f"Runtime dependencies changed ({modules}); review the package modules.")
    commit = checked(["git", "rev-parse", "HEAD"], root)
    dirty = bool(checked(["git", "status", "--porcelain"], root))
    name = f"line-tracker-{system}-{architecture}-{commit[:12]}" + ("-dirty" if dirty else "")
    destination = root / "target/dist"
    if (root / "target").is_symlink() or destination.is_symlink():
        raise RuntimeError("target and target/dist must be local directories, not symbolic links.")
    destination.mkdir(parents=True, exist_ok=True)
    extension = ".zip" if system == "windows" else ".tar.gz"
    archive = destination / (name + extension)
    if archive.exists():
        raise RuntimeError(f"Package already exists; preserve or remove that specific file: {archive}")
    with tempfile.TemporaryDirectory(prefix=".package-", dir=destination) as temporary:
        bundle = Path(temporary) / name
        (bundle / "app").mkdir(parents=True)
        shutil.copy2(jar, bundle / "app/line-tracker.jar")
        checked(
            [tools["jlink"], "--module-path", home / "jmods", "--add-modules", "java.base",
             "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2",
             "--output", bundle / "runtime"],
            root,
        )
        if not (bundle / "runtime/legal/java.base").is_dir():
            raise RuntimeError("Generated runtime is missing its java.base legal notices.")
        launcher = bundle / "line-tracker"
        launcher.write_text(POSIX_LAUNCHER, encoding="utf-8", newline="\n")
        launcher.chmod(0o755)
        (bundle / "line-tracker.cmd").write_text(
            WINDOWS_LAUNCHER, encoding="utf-8", newline="\r\n"
        )
        (bundle / "PACKAGE_README.md").write_text(PACKAGE_README, encoding="utf-8")
        for fixture in ("identical", "insert-delete"):
            target = bundle / "examples" / fixture
            target.mkdir(parents=True)
            for filename in ("old.txt", "new.txt"):
                shutil.copy2(root / "datasets/development" / fixture / filename, target / filename)
        information = {
            "schema_version": 1,
            "commit": commit,
            "dirty": dirty,
            "jdk": properties.get("JAVA_RUNTIME_VERSION", properties["JAVA_VERSION"]),
            "jdk_vendor": properties.get("IMPLEMENTOR", "unknown"),
            "platform": system,
            "architecture": architecture,
            "modules": ["java.base"],
            "jar_sha256": sha256(jar),
            "packaged_at_utc": datetime.now(timezone.utc).isoformat(),
        }
        (bundle / "build-info.json").write_text(
            json.dumps(information, indent=2) + "\n", encoding="utf-8"
        )
        write_archive(bundle, archive, system == "windows")
    print(archive)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, ET.ParseError, subprocess.TimeoutExpired) as error:
        print(f"Packaging failed: {error}", file=sys.stderr)
        sys.exit(1)
