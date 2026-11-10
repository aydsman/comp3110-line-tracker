"""Regression checks for scoring, CLI output, line counting and partial failures."""

import contextlib
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from evaluate_dataset import aggregate, main, pair_metrics, parse_pairs, score_case
from validate_dataset import source_lines


class EvaluationTests(unittest.TestCase):
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
            with patch("evaluate_dataset.subprocess.run", side_effect=responses), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(main(command), 1)
            report = json.loads(output.read_text())
            self.assertFalse(report["complete"])
            self.assertEqual(report["failed_cases"], 1)
            self.assertEqual(report["successful_cases"], 1)
            self.assertEqual(report["cases"][1]["f1"], 1)
            original_gold = (root / "expected.json").read_bytes()
            with patch("evaluate_dataset.subprocess.run") as runner, contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(main(command[:-1] + [str(root / "expected.json")]), 1)
                runner.assert_not_called()
            self.assertEqual((root / "expected.json").read_bytes(), original_gold)


if __name__ == "__main__":
    unittest.main()
