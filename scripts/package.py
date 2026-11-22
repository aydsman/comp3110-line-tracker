#!/usr/bin/env python3
"""Package the verified CLI and JavaFX GUI with their native Java 17 runtime."""

import hashlib
import json
import os
from pathlib import Path
import platform
import re
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

POSIX_GUI_LAUNCHER = POSIX_LAUNCHER.replace(
    '-jar "$app_dir/app/line-tracker.jar"',
    '--module-path "$app_dir/app/lib" --add-modules javafx.controls '
    '-cp "$app_dir/app/line-tracker.jar" edu.comp3110.linetracker.ComparisonApp',
)

WINDOWS_GUI_LAUNCHER = WINDOWS_LAUNCHER.replace(
    '-jar "%~dp0app\\line-tracker.jar"',
    '--module-path "%~dp0app\\lib" --add-modules javafx.controls '
    '-cp "%~dp0app\\line-tracker.jar" edu.comp3110.linetracker.ComparisonApp',
)

JAVAFX_MODULES = ("javafx.base", "javafx.graphics", "javafx.controls")
JAVAFX_CLASSIFIERS = {
    ("macos", "arm64"): "mac-aarch64", ("macos", "x86_64"): "mac",
    ("linux", "arm64"): "linux-aarch64", ("linux", "x86_64"): "linux",
    ("windows", "x86_64"): "win",
}

PACKAGE_README = '''# Line Tracker desktop and CLI package

This archive includes its own Java 17 runtime. It runs only on the operating
system and CPU architecture named in the archive and build-info.json.
No separate Java installation is needed. The GUI includes JavaFX and its native
libraries. A normal desktop session is required for the GUI (GTK 3 on Linux).

Extract the complete archive before running it. From the extracted directory:

macOS/Linux:

    ./line-tracker-gui
    ./line-tracker map examples/insert-delete/old.txt examples/insert-delete/new.txt
    ./line-tracker map OLD NEW --advanced --format json --output results.json

Windows PowerShell:

    .\\line-tracker-gui.cmd
    .\\line-tracker.cmd map examples\\insert-delete\\old.txt examples\\insert-delete\\new.txt
    .\\line-tracker.cmd map OLD NEW --advanced --format json --output results.json

Quote paths containing spaces. The first example prints 1-2 and 3-3.
Unmatched-line diagnostics go to stderr. Exit codes are 0 success, 1 comparison
or output failure, and 2 invalid usage. Advanced mode includes exact split
reconstruction; the default mode is the unchanged-line baseline.

Use the GUI to choose old/new UTF-8 files, compare with baseline or advanced
matching, select mappings to highlight the corresponding lines, and export
text or JSON. Both interfaces use the same validated comparison pipeline.

build-info.json records the source commit, dirty state, Java runtime modules,
platform, architecture, application/dependency checksums and JavaFX version.
Dirty means the build included uncommitted work.
The examples are synthetic development fixtures, not benchmark evidence.
Keep runtime/legal and legal/openjfx when redistributing the archive.
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


def native_architecture(data, system):
    """Inspect the native object header, rather than trusting a classifier name."""
    if system == "macos" and data[:4] == b"\xcf\xfa\xed\xfe":
        return {0x01000007: "x86_64", 0x0100000C: "arm64"}.get(
            int.from_bytes(data[4:8], "little")
        )
    if system == "linux" and len(data) >= 20 and data[:4] == b"\x7fELF" and data[4] == 2:
        order = "little" if data[5] == 1 else "big"
        return {62: "x86_64", 183: "arm64"}.get(int.from_bytes(data[18:20], order))
    if system == "windows" and data[:2] == b"MZ":
        offset = int.from_bytes(data[60:64], "little")
        if data[offset:offset + 4] == b"PE\0\0":
            return {0x8664: "x86_64", 0xAA64: "arm64"}.get(
                int.from_bytes(data[offset + 4:offset + 6], "little")
            )
    return None


def javafx_dependencies(root, system, architecture):
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    version = ET.parse(root / "pom.xml").findtext("m:properties/m:javafx.version", namespaces=namespace)
    if version != "17.0.21":
        raise RuntimeError("Review native libraries and legal notices before changing JavaFX 17.0.21.")
    classifier = JAVAFX_CLASSIFIERS.get((system, architecture))
    if classifier is None:
        raise RuntimeError(f"No supported JavaFX native classifier for {system}-{architecture}.")
    directory = root / "target/dependency"
    expected = {
        f"{module.replace('.', '-')}-{version}{suffix}.jar"
        for module in JAVAFX_MODULES for suffix in ("", "-" + classifier)
    }
    found = {path.name for path in directory.glob("*.jar")}
    if found != expected:
        raise RuntimeError(
            f"Runtime libraries differ from the pinned host JavaFX dependencies: {sorted(found)}. "
            "Run clean verify; review any added dependency before packaging."
        )
    libraries = []
    runtime_version = None
    native_suffix = {"macos": ".dylib", "linux": ".so", "windows": ".dll"}[system]
    for module in JAVAFX_MODULES:
        path = directory / f"{module.replace('.', '-')}-{version}-{classifier}.jar"
        with zipfile.ZipFile(path) as packed:
            if "module-info.class" not in packed.namelist():
                raise RuntimeError(f"JavaFX dependency is not modular: {path.name}")
            if module == "javafx.base":
                properties = dict(
                    line.split("=", 1)
                    for line in packed.read("javafx.properties").decode("utf-8").splitlines()
                    if "=" in line and not line.startswith(("#", "!"))
                )
                runtime_version = properties.get("javafx.runtime.version", "")
                if properties.get("javafx.version") != version or not re.fullmatch(
                    re.escape(version) + r"\+\d+", runtime_version
                ):
                    raise RuntimeError("Embedded OpenJFX release/runtime version does not match the POM.")
            natives = [name for name in packed.namelist() if name.endswith(native_suffix)]
            for name in natives:
                if native_architecture(packed.read(name), system) != architecture:
                    raise RuntimeError(f"Wrong native CPU architecture in {path.name}: {name}")
            if module == "javafx.graphics" and not any("glass" in name for name in natives):
                raise RuntimeError("JavaFX graphics is missing its native windowing library.")
        libraries.append((path, {"file": "app/lib/" + path.name, "module": module,
                                 "sha256": sha256(path), "native_libraries": sorted(natives)}))
    return version, runtime_version, classifier, libraries


def runtime_modules(root, tools, jar, libraries):
    module_path = os.pathsep.join(str(path) for path, _ in libraries)
    required = {"java.base"}
    for _, library in libraries:
        description = checked(
            [tools["java"], "--module-path", module_path, "--describe-module", library["module"]], root
        )
        # OpenJFX's Maven module descriptors omit @version; the base JAR's
        # javafx.properties is the authoritative embedded release/build record.
        identity = description.split()[0].split("@", 1)
        if identity[0] != library["module"] or (len(identity) == 2 and identity[1] != "17.0.21"):
            raise RuntimeError(f"Wrong JavaFX module/version in {library['file']}")
        for dependency in re.findall(r"^requires ([\w.]+)", description, flags=re.MULTILINE):
            if dependency.startswith(("java.", "jdk.")):
                required.add(dependency)
            elif dependency not in JAVAFX_MODULES:
                raise RuntimeError(f"Unreviewed JavaFX module dependency: {dependency}")
    # Resolve every application reference with the exact copied platform libraries.
    application = checked(
        [tools["jdeps"], "--multi-release", "17", "--module-path", module_path,
         "--add-modules", "javafx.controls", "--print-module-deps", jar], root
    )
    for dependency in application.split(","):
        if dependency.startswith(("java.", "jdk.")):
            required.add(dependency)
        elif dependency not in JAVAFX_MODULES:
            raise RuntimeError(f"Unreviewed application module dependency: {dependency}")
    return sorted(required)


def copy_javafx_legal(root, bundle, version):
    source = root / "scripts/legal/openjfx"
    provenance = json.loads((source / "provenance.json").read_text(encoding="utf-8"))
    if provenance["javafx_version"] != version:
        raise RuntimeError("OpenJFX legal files do not match the dependency version.")
    required = {"LICENSE", "ADDITIONAL_LICENSE_INFO", "ASSEMBLY_EXCEPTION", "jpeg_fx.md", "mesa3d.md"}
    if {entry["file"] for entry in provenance["files"]} != required:
        raise RuntimeError("OpenJFX legal-file inventory is incomplete.")
    for entry in provenance["files"]:
        if sha256(source / entry["file"]) != entry["sha256"]:
            raise RuntimeError(f"OpenJFX license checksum mismatch: {entry['file']}")
    shutil.copytree(source, bundle / "legal/openjfx")
    return provenance


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
    fx_version, fx_runtime_version, classifier, libraries = javafx_dependencies(root, system, architecture)
    modules = runtime_modules(root, tools, jar, libraries)
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
        (bundle / "app/lib").mkdir()
        for path, library in libraries:
            shutil.copy2(path, bundle / library["file"])
        legal = copy_javafx_legal(root, bundle, fx_version)
        checked(
            [tools["jlink"], "--module-path", home / "jmods", "--add-modules", ",".join(modules),
             "--strip-debug", "--no-header-files", "--no-man-pages", "--compress=2",
             "--output", bundle / "runtime"],
            root,
        )
        bundled_java = bundle / "runtime/bin" / ("java.exe" if system == "windows" else "java")
        included_modules = sorted(
            line.split("@")[0] for line in checked([bundled_java, "--list-modules"], root).splitlines()
        )
        for module in included_modules:
            if not (bundle / "runtime/legal" / module).is_dir():
                raise RuntimeError(f"Generated runtime is missing legal notices for {module}.")
        checked([bundled_java, "--module-path", bundle / "app/lib", "--add-modules",
                 "javafx.controls", "--validate-modules"], root)
        for name, posix, windows in (("line-tracker", POSIX_LAUNCHER, WINDOWS_LAUNCHER),
                                     ("line-tracker-gui", POSIX_GUI_LAUNCHER, WINDOWS_GUI_LAUNCHER)):
            launcher = bundle / name
            launcher.write_text(posix, encoding="utf-8", newline="\n")
            launcher.chmod(0o755)
            (bundle / (name + ".cmd")).write_text(windows, encoding="utf-8", newline="\r\n")
        (bundle / "PACKAGE_README.md").write_text(PACKAGE_README, encoding="utf-8")
        for fixture in ("identical", "insert-delete", "split-wrap", "both-empty"):
            target = bundle / "examples" / fixture
            target.mkdir(parents=True)
            for filename in ("old.txt", "new.txt"):
                shutil.copy2(root / "datasets/development" / fixture / filename, target / filename)
        information = {
            "schema_version": 2,
            "commit": commit,
            "dirty": dirty,
            "jdk": properties.get("JAVA_RUNTIME_VERSION", properties["JAVA_VERSION"]),
            "jdk_vendor": properties.get("IMPLEMENTOR", "unknown"),
            "platform": system,
            "architecture": architecture,
            "capabilities": ["cli", "gui"],
            "modules": included_modules,
            "javafx": {"version": fx_version, "runtime_version": fx_runtime_version,
                       "classifier": classifier,
                       "libraries": [library for _, library in libraries],
                       "legal_source_commit": legal["source_commit"]},
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
