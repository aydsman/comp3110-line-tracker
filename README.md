# COMP 3110 — Source Code Line Tracker

Track corresponding source-code lines between two versions of a file using a Java core shared by a CLI and a planned JavaFX interface.

## Status

This branch integrates the Java 17/Maven foundation and Ayden's Task 1 baseline: UTF-8 file loading, normalization that preserves original text and line numbers, deterministic LCS unchanged-line matching, normalized Levenshtein content similarity, and a runnable CLI. Ali's Task 1 adds a validated pipeline for paths or already loaded files, file errors that identify the affected input, and shared canonical text formatting. `verify` includes tests of the packaged JAR.

The foundation depends on [Ayden's unmerged pull request #1](https://github.com/aydsman/comp3110-line-tracker/pull/1). Advanced matching and QA evaluation exist on separate feature branches and are not integrated here. The JavaFX frontend and JSON exports are outside this branch. No benchmark accuracy is claimed. See [Ali's Task 1 handoff](docs/ALI_TASK_1.md) for acceptance evidence and outstanding reviews/course confirmations.

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

Use `./mvnw clean verify` (`.\mvnw.cmd clean verify` on Windows) for a clean build, unit tests, packaging, and integration tests that launch `target/line-tracker.jar`. The `package` phase alone does not run the packaged-JAR integration tests.

## Shared interface

```java
LineMatcher matcher = new BaselineMatcher();
MappingResult result = new MappingPipeline(matcher)
    .compare(oldPath, newPath, MatchConfig.DEFAULT);
String pairs = TextMappingFormatter.format(result);
```

All public core classes live in `edu.comp3110.linetracker`. The pipeline also accepts `compare(SourceFile oldFile, SourceFile newFile, MatchConfig config)` so a frontend can display and compare the same loaded source records. Both overloads enforce the same result contract. Aadi should load and compare files in a JavaFX background task; Ali can inject an advanced matcher through the existing `LineMatcher` constructor without changing the UI. See [architecture](docs/ARCHITECTURE.md) for models, errors, and handoff examples.

## Project goals and limits

Produce original one-based line mappings, contribute a verified dataset of at least 25 distinct files, and evaluate on the existing and new datasets. Confirm the bug-tracing requirement and exact reproduction requirements with the professor.

The baseline matches normalized equality in order. It does not yet identify modified/moved/split lines; its unmatched lines are candidates for later matching, not proof of semantic addition/deletion. Normalization lowercases and collapses whitespace even inside strings/comments, so an `UNCHANGED` result means normalized equality, not identical bytes or behavior. Original text is always retained. LCS uses O(n*m) time and memory with a configurable 4-million-cell default guard; oversized comparisons fail clearly. Content similarity uses Java UTF-16 code units and does not claim semantic equivalence.

Two synthetic development fixture pairs are included. They are not the required evaluation dataset and await independent teammate review. Mujtaba's QA branch uses the same `identical` fixture path for a different two-line example; this branch retains Ayden's three-line example. Reconcile that conflict explicitly during QA integration. Rubric, existing benchmark access, and mandatory bug-tracing scope remain unconfirmed.

## Project guide

- [Getting started and CI downloads](docs/GETTING_STARTED.md)
- [Architecture and interfaces](docs/ARCHITECTURE.md)
- [Ali's Task 1 evidence and handoff](docs/ALI_TASK_1.md)
- [Task 1–4 milestones and ownership](docs/PLAN.md)
- [Dataset and evaluation protocol](docs/DATASET.md)
- [Decisions and professor questions](docs/DECISIONS.md)
- [Contribution workflow](CONTRIBUTING.md)
- [Trello board](https://trello.com/b/6kk3pY0g/line-tracker)

## Branch workflow

Use `development` for shared integration. Branch each task from development and open its pull request back into development. Promote reviewed, tested code to `main` through a separate release pull request. See CONTRIBUTING.md for the workflow.

## Continuous integration

The workflow defines `verify` on Windows and Linux for pull requests targeting development/main and branch pushes, covering formatting, unit tests, JAR packaging and integration tests. The aggregate `test` check depends on both platforms. Check the actual run for the submitted commit before claiming CI success. Download JARs and available reports from that run's artifacts (14-day retention). Use `./mvnw spotless:apply` (`.\mvnw.cmd spotless:apply` on Windows) to fix formatting locally. Weekly Dependabot updates target development once its configuration is present on the default branch.
