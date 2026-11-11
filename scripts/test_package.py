#!/usr/bin/env python3
"""Extract and exercise a native CLI archive without relying on an installed Java."""

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


def main():
    require(sys.version_info >= (3, 12), "Package verification requires Python 3.12 or newer")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
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
        require(information["jdk"].startswith("17."), "Bundled Java must be Java 17")
        require(information["modules"] == ["java.base"], "Unexpected bundled runtime modules")
        jar = bundle / "app/line-tracker.jar"
        with jar.open("rb") as source:
            require(
                hashlib.file_digest(source, "sha256").hexdigest() == information["jar_sha256"],
                "JAR checksum differs from build-info.json",
            )
        require((bundle / "runtime/legal/java.base").is_dir(), "Missing runtime legal notices")
        launcher = bundle / ("line-tracker.cmd" if os.name == "nt" else "line-tracker")
        caller = scratch / "different working directory"
        caller.mkdir()
        empty_path = scratch / "empty executable search path"
        empty_path.mkdir()
        environment = os.environ.copy()
        for name in ("JAVA_HOME", "JRE_HOME", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
            environment.pop(name, None)
        environment["PATH"] = str(empty_path)
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
        print(
            f"PASS: {launches} packaged launcher checks; JSON parsed independently; "
            f"no JAVA_HOME or Java on PATH; {information['platform']}-{information['architecture']}"
        )


if __name__ == "__main__":
    main()
