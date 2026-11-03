# COMP 3110 — Source Code Line Tracker

Track corresponding source-code lines between two versions of a file using a Java core shared by a CLI and a planned JavaFX interface.

## Status

The Java 17/Maven foundation and backend Task 1 are implemented: UTF-8 file loading, normalization that preserves original text and line numbers, deterministic LCS unchanged-line matching, normalized Levenshtein content similarity, result validation, and a runnable baseline CLI. Advanced matching, the JavaFX frontend, JSON exports and dataset evaluation are still planned. No benchmark accuracy results have been produced.

## Team

| Member | Role | Responsibility |
| --- | --- | --- |
| Ayden | Backend | Baseline matching, SimHash candidates, similarity scoring, conflict resolution and split detection |
| Aadi | Frontend | JavaFX file selection, side-by-side comparison, mapping highlights and export controls |
| Ali | Integration | Shared interfaces, file processing, pipeline, CLI/exports, packaging and integration |
| Mujtaba | QA & Evaluation | Verified 25-file dataset, automated checks, accuracy evaluation and bug reports |

Ayden prepared the initial shared foundation to unblock the team. Ali owns its ongoing integration. Everyone tests and documents their own contribution; another teammate reviews before a task is Done. Bug tracing is a shared later milestone led by Ayden and Ali if required.

## Build and run

Install a JDK 17 or newer and make it available through JAVA_HOME or PATH. The wrapper downloads pinned Maven 3.9.9 on first use; the first build requires internet access for dependencies.

Windows PowerShell:

```powershell
.\mvnw.cmd verify
java -jar target/line-tracker.jar map datasets/development/insert-delete/old.txt datasets/development/insert-delete/new.txt
```

macOS/Linux:

```sh
./mvnw verify
java -jar target/line-tracker.jar map datasets/development/insert-delete/old.txt datasets/development/insert-delete/new.txt
```

Expected standard output:

```text
1-2
3-3
```

Unmatched old/new lines are printed on standard error (for this fixture: old `[2]`, new `[1, 4]`). Exit codes: 0 success, 1 comparison/file error, 2 incorrect usage. The CLI currently supports only `map OLD NEW`; JSON and evaluate commands are not implemented.

## Shared interface

```java
LineMatcher matcher = new BaselineMatcher();
MappingResult result = new MappingPipeline(matcher)
    .compare(oldPath, newPath, MatchConfig.DEFAULT);
```

All public core classes live in `edu.comp3110.linetracker`. Aadi should call the pipeline from a JavaFX background task; Ali can inject a future advanced matcher through LineMatcher without duplicating algorithms. See [architecture](docs/ARCHITECTURE.md) for data contracts.

## Project goals and limits

Produce original one-based line mappings, contribute a verified dataset of at least 25 distinct files, and evaluate on the existing and new datasets. Confirm the bug-tracing requirement and exact reproduction requirements with the professor.

The baseline matches normalized equality in order. It does not yet identify modified/moved/split lines; its unmatched lines are candidates for later matching, not proof of semantic addition/deletion. Normalization lowercases and collapses whitespace even inside strings/comments, so an `UNCHANGED` result means normalized equality, not identical bytes or behavior. Original text is always retained. LCS uses O(n*m) time and memory with a configurable 4-million-cell default guard; oversized comparisons fail clearly. Content similarity uses Java UTF-16 code units and does not claim semantic equivalence.

Two synthetic development fixture pairs are included. They are not the required evaluation dataset and await independent teammate review.

## Project guide

- [Architecture and interfaces](docs/ARCHITECTURE.md)
- [Task 1–4 milestones and ownership](docs/PLAN.md)
- [Dataset and evaluation protocol](docs/DATASET.md)
- [Decisions and professor questions](docs/DECISIONS.md)
- [Contribution workflow](CONTRIBUTING.md)
- [Trello board](https://trello.com/b/6kk3pY0g/line-tracker)
