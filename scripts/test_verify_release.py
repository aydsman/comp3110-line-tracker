"""Protect the release runner's partial-result and evidence-preservation contracts."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("verify_release", Path(__file__).with_name("verify_release.py"))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseVerificationTests(unittest.TestCase):
    def test_evidence_must_be_new_and_beneath_target(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = release.prepare_output(root, Path("target/qa"))
            sentinel = output / "summary.json"
            sentinel.write_text("original evidence", encoding="utf-8")
            with self.assertRaises(FileExistsError):
                release.prepare_output(root, Path("target/qa"))
            with self.assertRaises(ValueError):
                release.prepare_output(root, Path("datasets/new"))
            with self.assertRaises(ValueError):
                release.prepare_output(root, Path("target/../README.md"))
            self.assertEqual("original evidence", sentinel.read_text(encoding="utf-8"))

    def test_matrix_preserves_partitions_modes_and_explicit_benchmark_limit(self):
        root = Path("/repo")
        arguments = (root, root / "target/app.jar", Path("/jdk/bin/java"), "revision", root / "target/qa")
        tasks = release.steps(*arguments, None)
        evaluations = [task for task in tasks if task["kind"] == "evaluation"]
        self.assertEqual(6, len(evaluations))
        self.assertEqual([20, 5, 7, 20, 5, 7], [task["expected_cases"] for task in evaluations])
        self.assertFalse(any(task["kind"] == "existing" for task in tasks))
        for task in evaluations:
            self.assertEqual(task["name"].endswith("advanced"), "--advanced" in task["command"])
            self.assertNotIn("--max-lcs-cells", task["command"])
        existing = [task for task in release.steps(*arguments, Path("/data/EvaluationDataset.zip"))
                    if task["kind"] == "existing"]
        self.assertEqual(4, len(existing))
        for task in existing:
            command = task["command"]
            if task["allow_resource_limit"]:
                self.assertNotIn("--max-lcs-cells", command)
            else:
                self.assertEqual("8000000", command[command.index("--max-lcs-cells") + 1])

    def test_only_default_profile_accepts_documented_resource_failure(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            task = {"kind": "existing", "report": str(path), "expected_cases": 2,
                    "allow_resource_limit": True}
            report = {"complete": False, "cases": [
                {"status": "ok"},
                {"status": "error", "error": "CLI exit 1: Files exceed baseline LCS memory limit (4797910 cells)"},
            ]}
            path.write_text(json.dumps(report), encoding="utf-8")
            self.assertEqual("resource-limited", release.assess(task, 1, "", ""))
            task["allow_resource_limit"] = False
            with self.assertRaises(ValueError):
                release.assess(task, 1, "", "")
            task["allow_resource_limit"] = True
            report["cases"][1]["error"] = "CLI exit 1: malformed input"
            path.write_text(json.dumps(report), encoding="utf-8")
            with self.assertRaises(ValueError):
                release.assess(task, 1, "", "")

    def test_complete_report_cannot_omit_cases(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            path.write_text(json.dumps({"complete": True, "cases": [{"status": "ok"}]}), encoding="utf-8")
            with self.assertRaises(ValueError):
                release.assess({"kind": "evaluation", "report": str(path), "expected_cases": 20}, 0, "", "")

    def test_real_java_test_cannot_be_skipped(self):
        self.assertEqual("pass", release.assess({"kind": "tests"}, 0, "", "Ran 13 tests in 1.1s\nOK\n"))
        with self.assertRaises(ValueError):
            release.assess({"kind": "tests"}, 0, "", "Ran 13 tests in 1.1s\nOK (skipped=1)\n")

    def test_reported_defaults_must_match_actual_json(self):
        actual = {key: value for key, value in release.SETTINGS.items() if key != "max_split_fragments"}
        self.assertEqual("pass", release.assess({"kind": "config"}, 0, json.dumps({"config": actual}), ""))
        actual["minimum_score"] = 0.7
        with self.assertRaises(ValueError):
            release.assess({"kind": "config"}, 0, json.dumps({"config": actual}), "")


if __name__ == "__main__":
    unittest.main()
