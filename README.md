# COMP 3110 — Source Code Line Tracker

A team project to track corresponding lines between two versions of a source file, including edited, moved, and split lines. The planned approach follows the LHDiff introduction supplied in class.

**Status:** planning and repository scaffold. The matching tool has not been implemented yet. Java is the chosen implementation language. The proposed build uses Maven and Java 17.

## Team

| Member | Role | Main responsibility |
| --- | --- | --- |
| Ali | 1. File Processing & Basic Matching | Read files, normalize lines, detect unchanged lines, format output |
| Aadi | 2. Advanced Line Matching | SimHash candidates, similarity scoring, conflict resolution, line splits |
| Mujtaba | 3. Dataset & Evaluation | 25-file dataset, verified mappings, evaluation and results |
| Project owner (you) | 4. Integration & Bug Tracing | Shared interfaces, integration, tests, releases, bug-tracing extension |

Each member documents and tests their own work. The integration lead also helps Aadi with the advanced matching workload.

## Project goals

1. Given old and new versions of a file, output the matching line numbers.
2. Contribute a new dataset covering at least 25 distinct files with correct line-tracking information; evaluate on both the existing and new datasets.
3. Investigate tracing a bug-fix change back to a potentially bug-introducing change. Confirm whether this extension is mandatory.

The class slides describe normalization, diff-based unchanged matches, SimHash candidate selection, content/context similarity, conflict resolution, and line-split detection. They suggest 15 candidates, four surrounding lines on each side, and weights of 0.6 for content and 0.4 for context. These are starting settings, not measured results from our implementation.

## Planned usage (not implemented yet)

```text
java -jar target/line-tracker.jar map old.txt new.txt --format text
java -jar target/line-tracker.jar map old.txt new.txt --format json --output mappings.json
java -jar target/line-tracker.jar evaluate datasets/new/manifest.json --output results/new.json
```

Text output uses `old-new`, for example `4-8`. A split is represented as separate pairs, for example `4-8`, `4-9`, and `4-10`. JSON additionally records match kind, score, and unmatched lines. Line numbers are always one-based and refer to original input files.

## Repository guide

- [Architecture and interfaces](docs/ARCHITECTURE.md)
- [Milestones and task ownership](docs/PLAN.md)
- [Dataset and evaluation rules](docs/DATASET.md)
- [Decisions and questions for the professor](docs/DECISIONS.md)
- [Team contribution workflow](CONTRIBUTING.md)

Do not interpret the research benchmark numbers in the introduction as our own results. No accuracy results have been produced yet.
