"""Regression checks for scoring, CLI output, line counting and partial failures."""

import contextlib
import io
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from evaluate_dataset import aggregate, main, pair_metrics, parse_pairs, score_case
from validate_dataset import source_lines


class EvaluationTests(unittest.TestCase):
    @unittest.skipUnless(os.environ.get("LINE_TRACKER_TEST_JAR"), "Set LINE_TRACKER_TEST_JAR for real CLI integration")
    def test_real_advanced_cli_recovers_entire_split_fixture(self):
        root = Path(__file__).resolve().parent
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "split-report.json"
            with contextlib.redirect_stdout(io.StringIO()):
                result = main([str(root / "datasets/development/manifest.json"),
                    "--jar", os.environ["LINE_TRACKER_TEST_JAR"],
                    "--java", os.environ.get("LINE_TRACKER_TEST_JAVA", "java"),
                    "--code-revision", "integration-test", "--settings", "{}",
                    "--advanced", "--output", str(output)])
            self.assertEqual(result, 0)
            report = json.loads(output.read_text())
            split = next(case for case in report["cases"] if case["case_id"] == "split-wrap")
            self.assertEqual(split["predicted_pairs_detail"], [[1, 1], [1, 2], [2, 3]])
            self.assertEqual(split["split_group_exact_accuracy"], 1)
            self.assertEqual(report["matcher_mode"], "advanced")
            self.assertEqual(report["command_template"][-1], "--advanced")

    def test_output_cannot_overwrite_unselected_partition(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            cases = []
            for split in ("development", "test"):
                case = {"id": split, "split": split}
                for field in ("old", "new", "expected", "license_file", "new_license_file"):
                    path = root / f"{split}-{field}.txt"
                    path.write_bytes(b"keep this file unchanged\n")
                    case[field] = path.name
                cases.append(case)
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(cases))
            jar = root / "tool.jar"
            jar.write_bytes(b"mock jar")
            originals = {path: path.read_bytes() for path in root.iterdir()}
            for selected, other in (("development", "test"), ("test", "development")):
                for field in ("old", "new", "expected", "license_file", "new_license_file"):
                    target = root / next(case[field] for case in cases if case["id"] == other)
                    with self.subTest(selected=selected, field=field), patch("evaluate_dataset.subprocess.run") as runner, contextlib.redirect_stderr(io.StringIO()):
                        self.assertEqual(main([str(manifest), "--jar", str(jar),
                            "--code-revision", "test", "--settings", "{}", "--split", selected,
                            "--output", str(target)]), 1)
                        runner.assert_not_called()
                    self.assertEqual({path: path.read_bytes() for path in originals}, originals)

    def test_incorrect_and_missing_pairs(self):
        scores = pair_metrics({(1, 1), (2, 2), (3, 3)}, {(1, 1), (2, 2), (4, 4), (5, 5)})
        self.assertEqual(scores["true_positives"], 2)
        self.assertEqual(scores["precision"], 0.5)
        self.assertAlmostEqual(scores["recall"], 2 / 3)
        self.assertAlmostEqual(scores["f1"], 4 / 7)

    def test_empty_pair_rules(self):
        self.assertEqual(pair_metrics(set(), set())["f1"], 1)
        for expected, predicted in ((set(), {(1, 1)}), ({(1, 1)}, set())):
            scores = pair_metrics(expected, predicted)
            self.assertEqual([scores[key] for key in ("precision", "recall", "f1")], [0, 0, 0])

    def test_split_requires_the_entire_group(self):
        expected = {"matches": [{"old_lines": [1], "new_lines": [1, 2], "kind": "split"}], "deleted": [], "added": [3]}
        exact = score_case(expected, {(1, 1), (1, 2)}, 1, 3)
        partial = score_case(expected, {(1, 1)}, 1, 3)
        extra = score_case(expected, {(1, 1), (1, 2), (1, 3)}, 1, 3)
        self.assertEqual(exact["split_group_exact_accuracy"], 1)
        self.assertEqual(partial["split_group_exact_accuracy"], 0)
        self.assertEqual(extra["split_group_exact_accuracy"], 0)
        self.assertEqual(partial["unmatched_classification_accuracy"], 0.75)

    def test_no_split_is_not_reported_as_perfect_split_accuracy(self):
        result = score_case({"matches": [], "deleted": [], "added": []}, set(), 0, 0)
        self.assertIsNone(result["split_group_exact_accuracy"])
        self.assertEqual(result["unmatched_classification_accuracy"], 1)

    def test_cli_parser_accepts_splits_and_rejects_invalid_ownership(self):
        self.assertEqual(parse_pairs("1-1\n1-2\n", 1, 2), {(1, 1), (1, 2)})
        for output in ("noise", "0-1", "2-1", "1-1\n1-1", "1-1\n2-1", "1-1\n1-3"):
            with self.subTest(output=output), self.assertRaises(ValueError):
                parse_pairs(output, 1 if output == "2-1" else 2, 3)

    def test_pooling_uses_counts_instead_of_average_file_scores(self):
        cases = [{"status": "ok", **pair_metrics({(1, 1)}, set())},
                 {"status": "ok", **pair_metrics({(i, i) for i in range(9)}, {(i, i) for i in range(9)})},
                 {"status": "error"}]
        result = aggregate(cases)
        self.assertAlmostEqual(result["micro_over_successful_cases"]["recall"], 0.9)
        self.assertEqual(result["failed_cases"], 1)
        self.assertIsNone(aggregate([{"status": "error"}])["micro_over_successful_cases"])

    def test_line_count_matches_java_delimiters(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "source.txt"
            for content, expected in ((b"", []), (b"\n", [""]), (b"a\r\nb\r", ["a", "b"]),
                                      ("a\u2028b\n\n".encode(), ["a\u2028b", ""])):
                path.write_bytes(content)
                self.assertEqual(source_lines(path), expected)

    def test_cli_continues_after_timeout_and_saves_incomplete_report(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "old.txt").write_text("alpha\n")
            (root / "new.txt").write_text("alpha\n")
            (root / "expected.json").write_text(json.dumps({"schema_version": 1, "matches": [
                {"old_lines": [1], "new_lines": [1], "kind": "unchanged"}], "deleted": [], "added": []}))
            cases = [{"id": case_id, "old": "old.txt", "new": "new.txt", "expected": "expected.json"}
                     for case_id in ("timeout", "success")]
            manifest = root / "manifest.json"
            manifest.write_text(json.dumps(cases))
            jar = root / "tool.jar"
            jar.write_bytes(b"mock jar")
            output = root / "report.json"
            command = [str(manifest), "--jar", str(jar), "--code-revision", "test", "--settings", "{}", "--output", str(output)]
            responses = [subprocess.CompletedProcess([], 0, "", "Java test"),
                         subprocess.TimeoutExpired("java", 30), subprocess.CompletedProcess([], 0, "1-1\n", "")]
            # platform.platform() may lazily run uname through the same subprocess module.
            # Keep that unrelated metadata lookup outside the three simulated Java calls.
            with patch("evaluate_dataset.platform.platform", return_value="test-platform"):
                with patch("evaluate_dataset.subprocess.run", side_effect=responses) as runner, contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(main(command), 1)
                self.assertEqual(runner.call_count, 3)
            report = json.loads(output.read_text())
            self.assertEqual(report["platform"], "test-platform")
            self.assertFalse(report["complete"])
            self.assertEqual(report["failed_cases"], 1)
            self.assertEqual(report["successful_cases"], 1)
            self.assertEqual(report["cases"][0]["status"], "error")
            self.assertIn("timed out", report["cases"][0]["error"])
            self.assertEqual(report["cases"][1]["f1"], 1)
            original_gold = (root / "expected.json").read_bytes()
            with patch("evaluate_dataset.subprocess.run") as runner, contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(main(command[:-1] + [str(root / "expected.json")]), 1)
                runner.assert_not_called()
            self.assertEqual((root / "expected.json").read_bytes(), original_gold)


if __name__ == "__main__":
    unittest.main()
