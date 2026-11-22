#!/usr/bin/env python3
"""Exercise the extracted native CLI and real JavaFX GUI without installed Java."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import platform
import stat
import subprocess
import sys
import tarfile
import tempfile
import zipfile


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def extract(archive, destination):
    if archive.name.endswith(".tar.gz"):
        with tarfile.open(archive, "r:gz") as packed:
            # Python 3.12's data filter rejects paths and links escaping the extraction root.
            packed.extractall(destination, filter="data")
    elif archive.suffix == ".zip":
        with zipfile.ZipFile(archive) as packed:
            for member in packed.infolist():
                path = PurePosixPath(member.filename.replace("\\", "/"))
                require(
                    not path.is_absolute() and ".." not in path.parts and ":" not in member.filename,
                    f"Unsafe ZIP member: {member.filename}",
                )
                require(
                    not stat.S_ISLNK(member.external_attr >> 16),
                    f"Unexpected ZIP symlink: {member.filename}",
                )
            packed.extractall(destination)
    else:
        raise ValueError("Expected a .tar.gz or .zip package")
    roots = list(destination.iterdir())
    require(len(roots) == 1 and roots[0].is_dir(), "Package must contain exactly one root directory")
    return roots[0]


def launcher_command(launcher, arguments):
    if os.name != "nt":
        return [str(launcher), *arguments]
    # Pass a raw command line so cmd's outer quotes preserve the batch path and argument spaces.
    command_processor = os.environ.get("COMSPEC") or str(
        Path(os.environ["SystemRoot"]) / "System32/cmd.exe"
    )
    batch_command = subprocess.list2cmdline([str(launcher), *arguments])
    return f'"{command_processor}" /d /s /c "{batch_command}"'


def checksum(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def main():
    require(sys.version_info >= (3, 12), "Package verification requires Python 3.12 or newer")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    parser.add_argument("--report", type=Path, help="Create a new JSON acceptance report at this path")
    args = parser.parse_args()
    archive = args.archive.resolve(strict=True)
    with tempfile.TemporaryDirectory(prefix="line tracker package test ") as temporary:
        scratch = Path(temporary)
        extracted = scratch / "extracted package with spaces"
        extracted.mkdir()
        bundle = extract(archive, extracted)
        information = json.loads((bundle / "build-info.json").read_text(encoding="utf-8"))
        host = {"Darwin": "macos", "Linux": "linux", "Windows": "windows"}[platform.system()]
        require(information["platform"] == host, "Package must be tested on its target operating system")
        require(information["schema_version"] == 2, "Expected desktop-package metadata schema 2")
        require(information["jdk"].startswith("17."), "Bundled Java must be Java 17")
        require(information["capabilities"] == ["cli", "gui"], "Package must include CLI and GUI")
        require(
            {"java.base", "java.desktop", "java.xml", "jdk.unsupported"} <= set(information["modules"]),
            "Missing JavaFX runtime modules",
        )
        jar = bundle / "app/line-tracker.jar"
        require(checksum(jar) == information["jar_sha256"], "JAR checksum differs from build-info.json")
        for module in information["modules"]:
            require((bundle / "runtime/legal" / module).is_dir(), f"Missing {module} legal notices")
        javafx = information["javafx"]
        require(javafx["version"] == "17.0.21", "Unexpected JavaFX version")
        require(
            {library["module"] for library in javafx["libraries"]}
            == {"javafx.base", "javafx.graphics", "javafx.controls"},
            "Missing JavaFX modules",
        )
        for library in javafx["libraries"]:
            dependency = bundle / library["file"]
            require(dependency.parent == bundle / "app/lib", "Invalid dependency path in metadata")
            require(checksum(dependency) == library["sha256"], f"Changed dependency: {dependency.name}")
            require(
                dependency.name.endswith(f"-{javafx['version']}-{javafx['classifier']}.jar"),
                "JavaFX version/classifier differs from dependency filename",
            )
            with zipfile.ZipFile(dependency) as packed:
                require("module-info.class" in packed.namelist(), "Missing JavaFX module descriptor")
                for native in library["native_libraries"]:
                    require(native in packed.namelist(), f"Missing native library: {native}")
        legal = bundle / "legal/openjfx"
        provenance = json.loads((legal / "provenance.json").read_text(encoding="utf-8"))
        require(provenance["javafx_version"] == javafx["version"], "Wrong OpenJFX legal version")
        require(provenance["source_commit"] == javafx["legal_source_commit"], "Wrong OpenJFX legal source")
        require(len(provenance["files"]) == 5, "Missing OpenJFX license files")
        for entry in provenance["files"]:
            require(checksum(legal / entry["file"]) == entry["sha256"], "Changed OpenJFX legal notice")
        launcher = bundle / ("line-tracker.cmd" if os.name == "nt" else "line-tracker")
        caller = scratch / "different working directory"
        caller.mkdir()
        empty_path = scratch / "empty executable search path"
        empty_path.mkdir()
        environment = os.environ.copy()
        for name in ("JAVA_HOME", "JRE_HOME", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
            environment.pop(name, None)
        environment["PATH"] = str(empty_path)
        # Do not let a previously extracted JavaFX native cache mask missing bundled libraries.
        java_cache = scratch / "fresh JavaFX native cache"
        java_cache.mkdir()
        java = bundle / "runtime/bin" / ("java.exe" if os.name == "nt" else "java")
        module_check = subprocess.run(
            [str(java), "--list-modules"], cwd=caller, env=environment,
            capture_output=True, text=True, timeout=30,
        )
        require(module_check.returncode == 0, module_check.stderr)
        require(
            sorted(line.split("@")[0] for line in module_check.stdout.splitlines()) == information["modules"],
            "Runtime module inventory differs from metadata",
        )
        launches = 0

        def run(*arguments):
            nonlocal launches
            launches += 1
            return subprocess.run(
                launcher_command(launcher, list(arguments)),
                cwd=caller,
                env=environment,
                capture_output=True,
                timeout=30,
            )

        def success(result, output, diagnostics):
            require(result.returncode == 0, f"Launch failed: {result.stderr!r}")
            require(result.stdout == output.encode("utf-8"), f"Unexpected stdout: {result.stdout!r}")
            stderr = result.stderr.decode("utf-8").replace("\r\n", "\n")
            require(stderr == diagnostics, f"Unexpected stderr: {stderr!r}")

        def input_file(name, contents):
            path = caller / name
            path.write_text(contents, encoding="utf-8", newline="")
            return str(path)

        def example(name, side):
            return str(bundle / "examples" / name / (side + ".txt"))

        success(
            run("map", example("identical", "old"), example("identical", "new")),
            "1-1\n2-2\n3-3\n",
            "Unmatched old lines: []\nUnmatched new lines: []\n",
        )
        success(
            run("map", example("insert-delete", "old"), example("insert-delete", "new")),
            "1-2\n3-3\n",
            "Unmatched old lines: [2]\nUnmatched new lines: [1, 4]\n",
        )
        baseline_json = run(
            "map", example("insert-delete", "old"), example("insert-delete", "new"),
            "--format", "json",
        )
        require(baseline_json.returncode == 0, baseline_json.stderr)
        prediction = json.loads(baseline_json.stdout)
        require(prediction["schema_version"] == 1, "Unexpected JSON schema")
        require(
            [(item["old_lines"], item["new_lines"]) for item in prediction["matches"]]
            == [([1], [2]), ([3], [3])],
            "Baseline JSON groups differ from expected pairs",
        )
        require(prediction["deleted"] == [2] and prediction["added"] == [1, 4], "Wrong unmatched JSON")
        old = input_file("old edited file.txt", "before\nint count = 1;\nafter\n")
        new = input_file("new edited file.txt", "before\nint count = 2;\nafter\n")
        advanced = run("map", old, new, "--advanced", "--format", "json")
        require(advanced.returncode == 0, advanced.stderr)
        mapped = json.loads(advanced.stdout)
        require(
            [item["kind"] for item in mapped["matches"]] == ["unchanged", "modified", "unchanged"],
            "Advanced mode did not recover the edited line",
        )
        require(
            mapped["config"] == {
                "max_lcs_cells": 4_000_000, "candidate_limit": 15,
                "context_radius": 4, "minimum_score": 0.65,
            },
            "Export omitted or changed effective configurable settings",
        )
        old_split = input_file("old split file.txt", "return sum(a, b);\n")
        new_split = input_file("new split file.txt", "return sum(a,\n b);\n")
        success(
            run("map", old_split, new_split, "--advanced"),
            "1-1\n1-2\n",
            "Unmatched old lines: []\nUnmatched new lines: []\n",
        )
        exported_json = caller / "exported split result.json"
        result = run(
            "map", old_split, new_split, "--advanced", "--format", "json",
            "--output", str(exported_json),
        )
        require(result.returncode == 0 and result.stdout == b"", f"JSON file export failed: {result}")
        split = json.loads(exported_json.read_text(encoding="utf-8"))
        require(
            split["matches"] == [{"old_lines": [1], "new_lines": [1, 2], "kind": "split", "score": 1.0}],
            "Export did not preserve the complete split group",
        )
        exported_text = caller / "exported split result.txt"
        result = run("map", old_split, new_split, "--advanced", "--output", str(exported_text))
        require(result.returncode == 0 and result.stdout == b"", f"Text file export failed: {result}")
        require(exported_text.read_bytes() == b"1-1\n1-2\n", "Text export differs from stdout format")
        missing = run("map", old, str(caller / "missing new file.txt"))
        require(missing.returncode == 1 and missing.stdout == b"", "Missing file did not fail cleanly")
        require(b"Comparison failed: Cannot read new file" in missing.stderr, "Missing input role in error")
        require(b"Exception in thread" not in missing.stderr and b"\tat " not in missing.stderr, "Leaked stack trace")
        usage = run("map")
        require(usage.returncode == 2 and usage.stdout == b"", "Usage error has wrong exit or stdout")
        require(b"Usage:" in usage.stderr and b"--format" in usage.stderr, "Missing usage guidance")
        gui_launcher = bundle / ("line-tracker-gui.cmd" if os.name == "nt" else "line-tracker-gui")
        gui_report = caller / "GUI acceptance result.json"
        gui_environment = environment.copy()
        gui_environment["JAVA_TOOL_OPTIONS"] = f'"-Djavafx.cachedir={java_cache}"'
        gui = subprocess.run(
            launcher_command(gui_launcher, ["--smoke-test", str(gui_report)]),
            cwd=caller, env=gui_environment, capture_output=True, timeout=60,
        )
        require(gui.returncode == 0, f"Packaged GUI failed: {gui.stdout!r}\n{gui.stderr!r}")
        require(gui_report.is_file(), "GUI did not produce its real-control acceptance report")
        gui_evidence = json.loads(gui_report.read_text(encoding="utf-8"))
        require(gui_evidence.get("success") is True, f"GUI acceptance failed: {gui_evidence}")
        require(gui_evidence.get("checks", 0) >= 83, "GUI acceptance did not run its complete checks")
        if args.report:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            with args.report.open("x", encoding="utf-8") as report:
                json.dump({"success": True, "archive_sha256": checksum(archive),
                           "build": information, "cli_launches": launches,
                           "gui": gui_evidence, "java_home_unset": True,
                           "java_on_path": False}, report, indent=2)
                report.write("\n")
        print(
            f"PASS: {launches} CLI launcher checks and {gui_evidence['checks']} real GUI checks; "
            "JSON parsed independently; "
            f"no JAVA_HOME or Java on PATH; {information['platform']}-{information['architecture']}"
        )


if __name__ == "__main__":
    main()
