#!/usr/bin/env python3
"""Run the candidate CLI/dataset QA matrix; missing local benchmark data exits 3."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import sys
import time
from datetime import datetime, timezone


SETTINGS = {
    "max_lcs_cells": 4000000,
    "candidate_limit": 15,
    "context_radius": 4,
    "minimum_score": 0.65,
    "max_split_fragments": 4,
}
MANIFESTS = ("datasets/new/manifest.json", "datasets/development/manifest.json")


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare_output(root, output):
    destination = (root / output).resolve()
    target = (root / "target").resolve()
    if destination == target or not destination.is_relative_to(target):
        raise ValueError("QA evidence must use a new directory beneath target/")
    destination.mkdir(parents=True, exist_ok=False)
    return destination


def snapshot(root, jar, archive):
    paths = {jar, Path(__file__).resolve()}
    for name in ("evaluate_dataset.py", "evaluate_existing.py", "validate_dataset.py",
                 "audit_cli.py", "test_evaluate_dataset.py", "test_evaluate_existing.py"):
        paths.add(root / name)
    counts = []
    for relative in MANIFESTS:
        manifest = root / relative
        paths.add(manifest)
        cases = json.loads(manifest.read_text(encoding="utf-8"))
        counts.append(len(cases))
        for case in cases:
            for field in ("old", "new", "expected", "license_file", "new_license_file"):
                if field in case:
                    paths.add((manifest.parent / case[field]).resolve())
        if relative == MANIFESTS[0]:
            partitions = {part: sum(case.get("split") == part for case in cases)
                          for part in ("development", "test")}
            if partitions != {"development": 20, "test": 5}:
                raise ValueError("Expected 20 development and five reserved real cases")
    if counts != [25, 7]:
        raise ValueError("Expected 25 real cases and seven separate synthetic fixtures")
    if archive is not None:
        paths.add(archive)
    return {str(path): digest(path) for path in sorted(paths)}


def steps(root, jar, java, revision, output, archive):
    common = ["--jar", str(jar), "--java", str(java), "--code-revision", revision]
    tasks = [
        {"name": "evaluator-tests", "kind": "tests", "command":
         [sys.executable, "-m", "unittest", "-v", "test_evaluate_dataset", "test_evaluate_existing"]},
        {"name": "dataset-validation", "kind": "validation", "command":
         [sys.executable, "validate_dataset.py"]},
        {"name": "cli-audit", "kind": "audit", "command":
         [sys.executable, "audit_cli.py", *common]},
    ]
    for mode in ("baseline", "advanced"):
        advanced = ["--advanced"] if mode == "advanced" else []
        fixture = root / "datasets/development/identical"
        tasks.append({"name": "config-" + mode, "kind": "config", "command":
                      [str(java), "-jar", str(jar), "map", str(fixture / "old.txt"),
                       str(fixture / "new.txt"), *advanced, "--format", "json"]})
        for label, manifest, partition, count in (
            ("new-development", MANIFESTS[0], "development", 20),
            ("new-test", MANIFESTS[0], "test", 5),
            ("fixtures", MANIFESTS[1], None, 7),
        ):
            command = [sys.executable, "evaluate_dataset.py", manifest, *common,
                       "--settings", json.dumps(SETTINGS), *advanced]
            if partition:
                command.extend(["--split", partition])
            tasks.append({"name": label + "-" + mode, "kind": "evaluation",
                          "expected_cases": count, "command": command})
        if archive is not None:
            for cells in (4000000, 8000000):
                command = [sys.executable, "evaluate_existing.py", str(archive), *common,
                           *advanced]
                if cells != 4000000:
                    command.extend(["--max-lcs-cells", str(cells)])
                tasks.append({"name": f"existing-{mode}-{cells}", "kind": "existing",
                              "expected_cases": 28, "allow_resource_limit": cells == 4000000,
                              "command": command})
    for task in tasks:
        if task["kind"] in {"audit", "evaluation", "existing"}:
            task["report"] = str(output / (task["name"] + ".json"))
            task["command"].extend(["--output", task["report"]])
    return tasks


def assess(task, returncode, stdout, stderr):
    """A report's completeness is separate from accuracy and intentional guard limits."""
    kind = task["kind"]
    report = None
    if "report" in task:
        report = json.loads(Path(task["report"]).read_text(encoding="utf-8"))
    if kind in {"evaluation", "existing"}:
        count = task["expected_cases"]
        cases = report["cases"]
        if len(cases) != count:
            raise ValueError(f"Expected {count} evaluated cases, observed {len(cases)}")
        failures = [case for case in cases if case["status"] != "ok"]
        if returncode == 0 and report["complete"] is True and not failures:
            return "pass"
        if (kind == "existing" and task["allow_resource_limit"] and returncode == 1
                and report["complete"] is False and failures
                and all("Files exceed baseline LCS memory limit (" in case.get("error", "")
                        for case in failures)):
            return "resource-limited"
        raise ValueError("Evaluation failed or is incomplete; inspect its case report")
    if returncode != 0:
        raise ValueError(f"Command exited {returncode}")
    if kind == "tests":
        if not re.search(r"\bRan 13 tests? in ", stderr + stdout) or "skipped=" in stderr + stdout:
            raise ValueError("Expected all 13 evaluator tests to run without skips")
    elif kind == "validation":
        if "Validated 32 cases." not in stdout:
            raise ValueError("Expected 32 structurally validated cases")
    elif kind == "audit":
        if report["failed_checks"] != 0 or report["passed_checks"] != 71:
            raise ValueError("Expected 71 successful CLI audit checks")
    elif kind == "config":
        expected = {key: value for key, value in SETTINGS.items() if key != "max_split_fragments"}
        if json.loads(stdout)["config"] != expected:
            raise ValueError("Actual CLI defaults differ from reported evaluation settings")
    return "pass"


def git_state(root):
    def read(*arguments):
        process = subprocess.run(["git", *arguments], cwd=root, capture_output=True,
                                 text=True, encoding="utf-8", timeout=30)
        return process.stdout.strip() if process.returncode == 0 else None
    return {"head": read("rev-parse", "HEAD"),
            "worktree_status": read("status", "--porcelain", "--untracked-files=all")}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--java", type=Path, required=True, help="Path to the actual Java executable")
    parser.add_argument("--code-revision", required=True, help="Revision used to build the supplied JAR")
    parser.add_argument("--existing-zip", type=Path, help="Local EvaluationDataset.zip; never downloaded")
    parser.add_argument("--output", type=Path, default=Path("target/release-qa"))
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    try:
        jar, java = args.jar.resolve(strict=True), args.java.resolve(strict=True)
        archive = args.existing_zip.resolve(strict=True) if args.existing_zip else None
        before = snapshot(root, jar, archive)
        output = prepare_output(root, args.output)
    except (OSError, ValueError, KeyError) as error:
        parser.error(str(error))
    environment = os.environ.copy()
    environment.update(LINE_TRACKER_TEST_JAR=str(jar), LINE_TRACKER_TEST_JAVA=str(java))
    report = {
        "schema_version": 1, "created_utc": datetime.now(timezone.utc).isoformat(),
        "reported_code_revision": args.code_revision, "git_state": git_state(root),
        "jar": str(jar), "jar_sha256": digest(jar), "java": str(java),
        "python_version": platform.python_version(), "platform": platform.platform(),
        "settings": SETTINGS, "input_sha256": before, "steps": [],
        "existing_benchmark": "supplied" if archive else "missing-local-archive",
        "limitations": [
            "This command does not build the JAR; its revision is caller supplied and its hash is recorded.",
            "QA completion means the matrix ran, not perfect matching accuracy or release approval.",
            "The default benchmark profile may be resource-limited; its reports retain failed cases.",
            "The 8-million-cell benchmark profile must complete when an archive is supplied.",
            "GUI interaction, native package testing and peer/course acceptance are separate evidence.",
        ],
    }
    for task in steps(root, jar, java, args.code_revision, output, archive):
        entry = dict(task)
        start = time.monotonic()
        stdout, stderr = "", ""
        try:
            process = subprocess.run(task["command"], cwd=root, env=environment,
                                     capture_output=True, text=True, encoding="utf-8",
                                     errors="replace", timeout=3600)
            stdout, stderr = process.stdout, process.stderr
            entry["exit_code"] = process.returncode
            entry["status"] = assess(task, process.returncode, stdout, stderr)
        except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
            entry.update(status="fail", error=str(error))
        entry["elapsed_seconds"] = round(time.monotonic() - start, 3)
        for stream, content in (("stdout", stdout), ("stderr", stderr)):
            path = output / (task["name"] + "." + stream + ".txt")
            path.write_text(content, encoding="utf-8")
            entry[stream] = str(path)
        report["steps"].append(entry)
        print(f"{entry['status']}: {task['name']}", flush=True)
    try:
        report["changed_inputs"] = [path for path, previous in before.items()
                                    if not Path(path).is_file() or digest(Path(path)) != previous]
    except OSError as error:
        report["changed_inputs"] = [str(error)]
    failed = bool(report["changed_inputs"]) or any(item["status"] == "fail" for item in report["steps"])
    report["cli_and_new_dataset_complete"] = not report["changed_inputs"] and not any(
        item["status"] == "fail" and item["kind"] != "existing" for item in report["steps"])
    report["qa_status"] = "failed" if failed else "incomplete" if archive is None else "complete"
    report["complete"] = report["qa_status"] == "complete"
    report["finished_utc"] = datetime.now(timezone.utc).isoformat()
    (output / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"QA {report['qa_status']}: {output / 'summary.json'}", flush=True)
    if archive is None:
        print("Existing benchmark not rerun: supply --existing-zip /path/to/EvaluationDataset.zip.")
    return 1 if failed else 3 if archive is None else 0


if __name__ == "__main__":
    raise SystemExit(main())
