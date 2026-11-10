"""Evaluate the Java map CLI against one dataset manifest (Python 3.9+)."""

import argparse
import hashlib
import json
import os
import platform
import re
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

from validate_dataset import require, validate_case


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def metrics_from_counts(expected_count, predicted_count, correct):
    if expected_count == predicted_count == 0:
        precision = recall = f1 = 1.0
    elif expected_count == 0 or predicted_count == 0:
        precision = recall = f1 = 0.0
    else:
        precision = correct / predicted_count
        recall = correct / expected_count
        f1 = 2 * correct / (expected_count + predicted_count)
    return {"true_positives": correct, "expected_pairs": expected_count,
            "predicted_pairs": predicted_count, "precision": precision,
            "recall": recall, "f1": f1}


def pair_metrics(expected, predicted):
    return metrics_from_counts(len(expected), len(predicted), len(expected & predicted))


def parse_pairs(output, old_count, new_count):
    pairs = set()
    new_owners = {}
    targets_by_old = {}
    for line in output.splitlines():
        match = re.fullmatch(r"([1-9]\d*)-([1-9]\d*)", line)
        require(match is not None, f"Invalid CLI mapping: {line!r}")
        old, new = map(int, match.groups())
        require(old <= old_count and new <= new_count, "Predicted line outside input file")
        require((old, new) not in pairs, "Duplicate predicted pair")
        require(new not in new_owners, "New line belongs to multiple old lines")
        new_owners[new] = old
        targets_by_old.setdefault(old, []).append(new)
        pairs.add((old, new))
    for values in targets_by_old.values():
        targets = sorted(values)
        require(targets == list(range(targets[0], targets[-1] + 1)), "Nonconsecutive split targets")
    return pairs


def score_case(expected, predicted, old_count, new_count):
    gold = {(old, new) for group in expected["matches"]
            for old in group["old_lines"] for new in group["new_lines"]}
    deleted = set(range(1, old_count + 1)) - {old for old, _ in predicted}
    added = set(range(1, new_count + 1)) - {new for _, new in predicted}
    unmatched_errors = len(deleted ^ set(expected["deleted"])) + len(added ^ set(expected["added"]))
    splits = [group for group in expected["matches"] if group["kind"] == "split"]
    targets_by_old = {}
    for old, new in predicted:
        targets_by_old.setdefault(old, set()).add(new)
    correct_splits = sum(
        targets_by_old.get(group["old_lines"][0], set()) == set(group["new_lines"])
        for group in splits
    )
    return {
        **pair_metrics(gold, predicted),
        "missing_pairs": sorted(gold - predicted),
        "unexpected_pairs": sorted(predicted - gold),
        "predicted_pairs_detail": sorted(predicted),
        "predicted_deleted": sorted(deleted), "predicted_added": sorted(added),
        "expected_split_groups": len(splits), "correct_split_groups": correct_splits,
        "split_group_exact_accuracy": correct_splits / len(splits) if splits else None,
        "unmatched_classification_correct": old_count + new_count - unmatched_errors,
        "unmatched_classification_total": old_count + new_count,
        "unmatched_classification_accuracy": 1 - unmatched_errors / (old_count + new_count) if old_count + new_count else 1.0,
    }


def evaluate_case(manifest_path, case, command, timeout):
    old_count, new_count = validate_case(manifest_path, case, check_provenance=False)
    base = manifest_path.parent
    old_path, new_path = base / case["old"], base / case["new"]
    expected_path = base / case["expected"]
    expected = json.loads(expected_path.read_text(encoding="utf-8"))
    for side, path in (("old", old_path), ("new", new_path)):
        if f"{side}_sha256" in case:
            require(sha256(path) == case[f"{side}_sha256"], f"{side}: manifest checksum differs")
    start = time.perf_counter()
    completed = subprocess.run(command + ["map", str(old_path), str(new_path)],
                               capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
    elapsed = (time.perf_counter() - start) * 1000
    require(completed.returncode == 0, f"CLI exit {completed.returncode}: {completed.stderr[:4000]}")
    predicted = parse_pairs(completed.stdout, old_count, new_count)
    return {
        "status": "ok", "old_line_count": old_count, "new_line_count": new_count,
        "old_sha256": sha256(old_path), "new_sha256": sha256(new_path),
        "expected_sha256": sha256(expected_path),
        "elapsed_ms_including_jvm_startup": elapsed,
        "cli_stderr": completed.stderr[:4000],
        **score_case(expected, predicted, old_count, new_count),
    }


def aggregate(cases):
    successful = [case for case in cases if case["status"] == "ok"]
    expected_total = sum(case["expected_pairs"] for case in successful)
    predicted_total = sum(case["predicted_pairs"] for case in successful)
    correct = sum(case["true_positives"] for case in successful)
    scores = metrics_from_counts(expected_total, predicted_total, correct) if successful else None
    split_total = sum(case.get("expected_split_groups", 0) for case in successful)
    split_correct = sum(case.get("correct_split_groups", 0) for case in successful)
    line_total = sum(case.get("unmatched_classification_total", 0) for case in successful)
    line_correct = sum(case.get("unmatched_classification_correct", 0) for case in successful)
    return {"successful_cases": len(successful), "failed_cases": len(cases) - len(successful),
            "empty_expected_pair_cases": sum(case["expected_pairs"] == 0 for case in successful),
            "micro_over_successful_cases": scores,
            "expected_split_groups": split_total, "correct_split_groups": split_correct,
            "split_group_exact_accuracy": split_correct / split_total if split_total else None,
            "unmatched_classification_accuracy": line_correct / line_total if line_total else (1.0 if successful else None),
            "total_elapsed_ms_including_jvm_startup": sum(case.get("elapsed_ms_including_jvm_startup", 0) for case in successful)}


def write_report(path, report):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=path.parent,
                                         prefix=path.name + ".", delete=False) as handle:
            temporary = Path(handle.name)
            json.dump(report, handle, indent=2, allow_nan=False)
            handle.write("\n")
        os.replace(temporary, path)
    finally:
        if temporary is not None and temporary.exists():
            temporary.unlink()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--java", default="java")
    parser.add_argument("--code-revision", required=True, help="Revision used to build the supplied JAR")
    parser.add_argument("--settings", required=True, help="JSON object describing effective matcher settings")
    parser.add_argument("--split", choices=("development", "test"))
    parser.add_argument("--timeout", type=float, default=30)
    parser.add_argument("--output", type=Path, default=Path("results/evaluation.json"))
    args = parser.parse_args(argv)
    try:
        require(args.timeout > 0 and args.timeout < float("inf"), "Timeout must be finite and positive")
        manifest_path = args.manifest.resolve()
        settings = json.loads(args.settings)
        require(isinstance(settings, dict), "Settings must be a JSON object")
        cases = json.loads(manifest_path.read_text(encoding="utf-8"))
        require(isinstance(cases, list) and cases, "Manifest must be a nonempty array")
        require(all(isinstance(case, dict) for case in cases), "Each manifest case must be an object")
        ids = [case["id"] for case in cases]
        require(all(isinstance(case_id, str) and case_id for case_id in ids), "Each case needs a nonempty string ID")
        require(len(ids) == len(set(ids)), "Duplicate case IDs")
        if args.split:
            cases = [case for case in cases if case.get("split") == args.split]
        require(bool(cases), "No cases match the requested split")
        jar = args.jar.resolve()
        require(jar.is_file(), "JAR file does not exist")
        protected = {manifest_path, jar}
        for case in cases:
            for field in ("old", "new", "expected", "license_file"):
                if field in case:
                    protected.add((manifest_path.parent / case[field]).resolve())
        require(args.output.resolve() not in protected, "Output must not overwrite dataset inputs or the JAR")
        version = subprocess.run([args.java, "-version"], capture_output=True, text=True, timeout=args.timeout, check=True)
        results = []
        for case in cases:
            entry = {"case_id": case["id"], "split": case.get("split"),
                     "reviewer": case.get("reviewer"), "review_status": case.get("review_status", "unspecified")}
            try:
                entry.update(evaluate_case(manifest_path, case, [args.java, "-jar", str(jar)], args.timeout))
            except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
                entry.update(status="error", error=str(error))
            results.append(entry)
            print(f"{case['id']}: " + (f"{entry['true_positives']}/{entry['expected_pairs']} correct, F1 {entry['f1']:.3f}" if entry["status"] == "ok" else entry["error"]))
        report = {
            "schema_version": 1, "created_utc": datetime.now(timezone.utc).isoformat(),
            "complete": all(case["status"] == "ok" for case in results),
            "manifest": str(args.manifest),
            "manifest_sha256": sha256(manifest_path), "jar_sha256": sha256(jar),
            "evaluator_sha256": sha256(Path(__file__)),
            "validator_sha256": sha256(Path(__file__).with_name("validate_dataset.py")),
            "reported_code_revision": args.code_revision, "reported_matcher_settings": settings,
            "java_version": (version.stderr + version.stdout).strip(),
            "python_version": platform.python_version(), "platform": platform.platform(),
            "command_template": [args.java, "-jar", str(jar), "map", "OLD", "NEW"],
            "selected_split": args.split,
            "limitations": ["Review status is recorded without granting approval.",
                            "Runtime includes JVM startup; one run per case.",
                            "Revision and settings are supplied by the caller; JAR checksum identifies the executed binary.",
                            "Text output supplies pairs only; split structure and unmatched lines are inferred. Match-kind labels are not scored.",
                            "Failed cases are excluded from pooled scores and make the run incomplete."],
            **aggregate(results), "cases": results,
        }
        write_report(args.output, report)
        print(f"Saved {args.output}")
        return 0 if report["complete"] else 1
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError) as error:
        print(f"Evaluation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
