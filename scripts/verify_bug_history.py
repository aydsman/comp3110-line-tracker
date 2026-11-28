#!/usr/bin/env python3
"""Reproduce the recorded Unicode-boundary bug from exact Git source snapshots."""

import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile


INTRODUCTION = "933763e319f6ba8b9d90defc04ab7f49d5d92cfd"
FIX_PARENT = "2f133fc2e52799da717a70449b4a099aedbe30fc"
FIX = "a2c5f16679cc5d5fea508fbcb42e9bee52837dff"
SOURCE = "src/main/java/edu/comp3110/linetracker/Normalizer.java"
PROBE = r'''
package edu.comp3110.linetracker;
import java.util.List;
public final class HistoryProbe {
  public static void main(String[] args) {
    var oldFile = SourceFile.fromLines("old", List.of("\u00a0RETURN X;\u00a0"));
    var newFile = SourceFile.fromLines("new", List.of("return x;"));
    var result = new BaselineMatcher().match(oldFile, newFile, MatchConfig.DEFAULT);
    System.out.println(result.matches().size());
    System.out.println(oldFile.lines().get(0).normalized());
  }
}
'''


def checked(command, cwd):
    return subprocess.run(command, cwd=cwd, check=True, capture_output=True,
                          text=True, encoding="utf-8", timeout=90).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--output", type=Path, default=Path("target/bug-history.json"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if not args.java_home:
        parser.error("Set JAVA_HOME or provide --java-home to a JDK 17+ installation")
    suffix = ".exe" if os.name == "nt" else ""
    java = args.java_home / "bin" / ("java" + suffix)
    javac = args.java_home / "bin" / ("javac" + suffix)
    for revision in (INTRODUCTION, FIX_PARENT, FIX):
        checked(["git", "cat-file", "-e", revision + "^{commit}"], root)
    parent = checked(["git", "rev-parse", FIX + "^"], root).strip()
    if parent != FIX_PARENT:
        raise RuntimeError("The pinned fix parent does not match repository history")
    results = []
    with tempfile.TemporaryDirectory(prefix="line-tracker-history-") as temporary:
        for revision, expected in ((INTRODUCTION, 0), (FIX_PARENT, 0), (FIX, 1)):
            snapshot = Path(temporary) / revision
            snapshot.mkdir()
            paths = checked(["git", "ls-tree", "-r", "--name-only", revision,
                             "src/main/java"], root).splitlines()
            sources = []
            for relative in paths:
                if not relative.endswith(".java"):
                    continue
                destination = snapshot / relative
                destination.parent.mkdir(parents=True, exist_ok=True)
                destination.write_text(checked(["git", "show", revision + ":" + relative], root),
                                       encoding="utf-8")
                sources.append(str(destination))
            probe = snapshot / "HistoryProbe.java"
            probe.write_text(PROBE, encoding="utf-8")
            classes = snapshot / "classes"
            classes.mkdir()
            checked([str(javac), "--release", "17", "-encoding", "UTF-8", "-d",
                     str(classes), *sources, str(probe)], snapshot)
            output = checked([str(java), "-cp", str(classes),
                              "edu.comp3110.linetracker.HistoryProbe"], snapshot).splitlines()
            matched = int(output[0])
            if matched != expected:
                raise AssertionError(f"{revision}: expected {expected} matches, got {matched}")
            results.append({"revision": revision, "matched_pairs": matched,
                            "normalized_old": output[1], "expected_pairs": expected})
    blame = checked(["git", "blame", "-w", "--line-porcelain", FIX_PARENT,
                     "--", SOURCE], root)
    candidate_lines = []
    current = None
    for line in blame.splitlines():
        parts = line.split()
        if len(parts) >= 3 and len(parts[0]) == 40:
            current = parts[0]
        if line.startswith("\t") and ".strip()" in line:
            candidate_lines.append({"commit": current, "source": line[1:]})
    report = {"schema_version": 1, "case": "unicode-boundary-whitespace",
              "input_old": "\u00a0RETURN X;\u00a0", "input_new": "return x;",
              "fix": FIX, "fix_parent": FIX_PARENT, "candidate_introduction": INTRODUCTION,
              "observed_regression": results, "blame_candidates": candidate_lines,
              "scope": "A verified local project-history example, not a general SZZ implementation, "
                       "external bug report, or proof of arbitrary historical causation.",
              "course_acceptance": "unconfirmed"}
    destination = args.output if args.output.is_absolute() else root / args.output
    # Reports are build artifacts; never overwrite source, labels or project documents.
    if not destination.resolve().is_relative_to((root / "target").resolve()):
        raise ValueError("History evidence must be written beneath the project's target directory")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(f"PASS: bug reproduced at introduction and fix parent; fix yields 1 matching pair. {destination}")


if __name__ == "__main__":
    main()
