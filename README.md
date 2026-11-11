# COMP 3110 — Source Code Line Tracker

Track corresponding source-code lines between two versions of a file using a Java core shared by a CLI and a planned JavaFX interface.

## Status

This branch integrates Ayden's baseline, advanced matcher and exact split detection with Ali's shared pipeline, CLI, text/JSON exports and native CLI packaging. The Java 17/Maven core reads UTF-8 inputs while preserving original text and line numbers, validates complete/exclusive mappings, and reports file errors with input context. The baseline matches normalized equality; `--advanced` additionally recovers edited, moved and split lines. Both modes use the same loaded-source or path pipeline. `verify` includes tests of the packaged JAR.

Ayden authored the foundation and matching algorithms, including Task 3 splits; Ali authored the integration boundary, shared formatters, export controls and packaging. The branch preserves work from [Ayden's foundation pull request #1](https://github.com/aydsman/comp3110-line-tracker/pull/1) and his later backend branches. These dependencies and Ali's changes require review through `development`. The actual JavaFX frontend and QA dataset integration remain separate handoffs. No benchmark accuracy is claimed. See [export usage and schema](docs/EXPORT_FORMAT.md), [packaging](docs/PACKAGING.md) and the task handoffs for revision-specific evidence.

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

Unmatched old/new lines are printed on standard error (for this fixture: old `[2]`, new `[1, 4]`). Exit codes: 0 success/help, 1 comparison/file error, 2 incorrect usage. Omitting options keeps the baseline text command unchanged for existing QA tooling.

```sh
java -jar target/line-tracker.jar map old.txt new.txt --advanced --format json
java -jar target/line-tracker.jar map old.txt new.txt --advanced --format json --output mappings.json
```

Options follow the two paths and may appear in any order. `--output` creates a new UTF-8 file and refuses to replace an existing file; stdout is empty when exporting to a file. JSON includes match kinds/scores, unmatched lines and the actual requested settings. See [all options and the export contract](docs/EXPORT_FORMAT.md). JSON predictions are not reviewed dataset labels.

An advanced split maps one old line to two through four consecutive new lines. Text emits each pair (for example `4-8` and `4-9`); JSON retains one `split` group. This detector requires exact reconstruction of the normalized old line and does not recover splits with additional edits.

Use `./mvnw clean verify` (`.\mvnw.cmd clean verify` on Windows) for a clean build, unit tests, packaging, and integration tests that launch `target/line-tracker.jar`. The `package` phase alone does not run the packaged-JAR integration tests.

## Native CLI bundles

The Task 3 packaging scripts create a native archive containing the JAR, launcher, examples and a Java 17 runtime, so users do not need a separate Java installation. Build each bundle on its target operating system/architecture using the pinned toolchain, then test the extracted archive. See [PACKAGING.md](docs/PACKAGING.md) for clean-checkout commands, requirements, build metadata and smoke checks. Bundles contain the CLI; an actual JavaFX frontend has not been integrated. Package scripts and local tests do not establish that another platform's archive has passed.

## Shared interface

```java
LineMatcher matcher = new BaselineMatcher();
MappingResult result = new MappingPipeline(matcher)
    .compare(oldPath, newPath, MatchConfig.DEFAULT);
String pairs = TextMappingFormatter.format(result);
String json = JsonMappingFormatter.format(result);
```

All public core classes live in `edu.comp3110.linetracker`. The pipeline also accepts `compare(SourceFile oldFile, SourceFile newFile, MatchConfig config)` so a frontend can display and compare the same loaded source records. Both overloads enforce the same result contract. Aadi should load and compare files in a JavaFX background task; select `new AdvancedMatcher()` through the existing `LineMatcher` constructor for advanced results. Use the same text/JSON formatters for GUI exports. The real GUI handoff remains unverified. See [architecture](docs/ARCHITECTURE.md) for models, errors, and examples.

## Project goals and limits

Produce original one-based line mappings, contribute a verified dataset of at least 25 distinct files, and evaluate on the existing and new datasets. Confirm the bug-tracing requirement and exact reproduction requirements with the professor.

The baseline matches normalized equality in order. It does not yet identify modified/moved/split lines; its unmatched lines are candidates for later matching, not proof of semantic addition/deletion. Normalization lowercases and collapses whitespace even inside strings/comments, so an `UNCHANGED` result means normalized equality, not identical bytes or behavior. Original text is always retained. LCS uses O(n*m) time and memory with a configurable 4-million-cell default guard; oversized comparisons fail clearly. Content similarity uses Java UTF-16 code units and does not claim semantic equivalence.

The advanced matcher preserves baseline anchors, uses SimHash to shortlist remaining lines, and combines content/context similarity before deterministic conflict resolution. It accepts exact moves, then exact splits of two through four available nonblank lines, then fuzzy edits. Candidate pruning and greedy selection can miss valid mappings; MOVED is the classification of a recovered exact match outside the LCS, not independently established edit history. Ayden's proposed evaluation settings are retained, but no independent teammate approval of an evaluation freeze is recorded.

Two synthetic development fixture pairs are included. They are not the required evaluation dataset and await independent teammate review. Mujtaba's QA branch uses the same `identical` fixture path for a different two-line example; this branch retains Ayden's three-line example. Reconcile that conflict explicitly during QA integration. Rubric, existing benchmark access, and mandatory bug-tracing scope remain unconfirmed.

## Project guide

- [Getting started and CI downloads](docs/GETTING_STARTED.md)
- [Architecture and interfaces](docs/ARCHITECTURE.md)
- [CLI options and text/JSON export schema](docs/EXPORT_FORMAT.md)
- [Native CLI packaging and clean-checkout verification](docs/PACKAGING.md)
- [Ali's Task 1 evidence and handoff](docs/ALI_TASK_1.md)
- [Ali's Task 2 integration and evidence](docs/ALI_TASK_2.md)
- [Ali's Task 3 packaging and acceptance](docs/ALI_TASK_3.md)
- [Task 1–4 milestones and ownership](docs/PLAN.md)
- [Dataset and evaluation protocol](docs/DATASET.md)
- [Decisions and professor questions](docs/DECISIONS.md)
- [Contribution workflow](CONTRIBUTING.md)
- [Trello board](https://trello.com/b/6kk3pY0g/line-tracker)

## Branch workflow

Use `development` for shared integration. Branch each task from development and open its pull request back into development. Promote reviewed, tested code to `main` through a separate release pull request. See CONTRIBUTING.md for the workflow.

## Continuous integration

The workflow defines builds on Ubuntu, Windows and macOS using pinned Temurin 17.0.21+9 and Python 3.12. Each runner performs Maven `verify`, builds a native Java-runtime bundle, tests its extracted archive and uploads the result. The aggregate `test` check requires all three platforms. Check the actual run for the submitted commit before claiming CI or package success. Download native archives, JARs and available reports from that run's artifacts (14-day retention). Use `./mvnw spotless:apply` (`.\mvnw.cmd spotless:apply` on Windows) to fix formatting locally. Weekly Dependabot updates target development once its configuration is present on the default branch.
