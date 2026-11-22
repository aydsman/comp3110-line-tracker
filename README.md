# COMP 3110 — Source Code Line Tracker

Track corresponding source-code lines between two UTF-8 file versions through a command line or JavaFX desktop application.

## Status

The `ali/task-4-release` candidate integrates the backend, desktop interface, shared text/JSON exports, native packaging and QA dataset. Baseline mode matches normalized equality; advanced mode also recovers edits, moves and exact 2–4-line splits. The Java 17 core preserves original text and line numbers and validates complete, exclusive mappings. The desktop offers both modes, an explicit benchmark resource profile, group highlights and exports using the same pipeline and formatters as the CLI.

Git history preserves Ayden's backend, Aadi's original JavaFX shell and Mujtaba's QA work. Ali's release integration connects the remaining desktop features and extends the runtime package. This is a candidate for reviewed integration into `development`; final QA approval and submission are separate. See [the release handoff](docs/ALI_TASK_4.md), [report](docs/PROJECT_REPORT.md), [demo](docs/DEMO.md) and [packaging](docs/PACKAGING.md).

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

For the desktop, run `./mvnw javafx:run` (`.\mvnw.cmd javafx:run` on Windows). Choose the old and new files, select Baseline or Advanced, and compare. Select a result to highlight every mapped fragment; unmatched rows identify additions/deletions. Choose Text or JSON and export to a new filename. Changing files or settings clears stale results.

## Native desktop and CLI bundles

The package contains a Java 17 runtime, pinned JavaFX libraries, launchers and examples. After extracting the appropriate OS/CPU archive, run `line-tracker-gui` or `line-tracker-gui.cmd` for the desktop, and `line-tracker` or `line-tracker.cmd` for CLI commands. No separate Java installation is required. Keep all extracted folders together. See [PACKAGING.md](docs/PACKAGING.md) for clean-checkout commands, legal notices, provenance and real GUI/CLI smoke checks. Each archive must be tested on its target operating system.

## Shared interface

```java
LineMatcher matcher = new BaselineMatcher();
MappingResult result = new MappingPipeline(matcher)
    .compare(oldPath, newPath, MatchConfig.DEFAULT);
String pairs = TextMappingFormatter.format(result);
String json = JsonMappingFormatter.format(result);
```

All public core classes live in `edu.comp3110.linetracker`. The pipeline also accepts `compare(SourceFile oldFile, SourceFile newFile, MatchConfig config)` so the desktop displays the exact snapshots used for matching. Both overloads enforce the same result contract. Desktop work runs in a JavaFX background task and exports use the shared formatters. See [architecture](docs/ARCHITECTURE.md) for models, errors and examples.

## Project goals and limits

Produce original one-based line mappings, contribute a verified dataset of at least 25 distinct files, and evaluate on the existing and new datasets. Confirm the bug-tracing requirement and exact reproduction requirements with the professor.

The baseline matches normalized equality in order. It does not yet identify modified/moved/split lines; its unmatched lines are candidates for later matching, not proof of semantic addition/deletion. Normalization lowercases and collapses whitespace even inside strings/comments, so an `UNCHANGED` result means normalized equality, not identical bytes or behavior. Original text is always retained. LCS uses O(n*m) time and memory with a configurable 4-million-cell default guard; oversized comparisons fail clearly. Content similarity uses Java UTF-16 code units and does not claim semantic equivalence.

The advanced matcher preserves baseline anchors, uses SimHash to shortlist remaining lines, and combines content/context similarity before deterministic conflict resolution. It accepts exact moves, then exact splits of two through four available nonblank lines, then fuzzy edits. Candidate pruning and greedy selection can miss valid mappings; MOVED is the classification of a recovered exact match outside the LCS, not independently established edit history. Ayden's proposed evaluation settings are retained, but no independent teammate approval of an evaluation freeze is recorded.

The integrated dataset contains 25 real source-file pairs and seven separate synthetic fixtures. The three-line foundation `identical` fixture and the two-line QA `identical-qa` fixture both retain their original inputs and answers. Recorded reviews are linked in the manifests; source review is distinct from release approval. Run `python scripts/verify_release.py --help` for reproducible candidate QA. The private existing-benchmark archive must be supplied for a fresh existing-data matrix. The course rubric and mandatory tracing scope remain unconfirmed; a [verified project-history example](docs/BUG_TRACING_EXAMPLE.md) is provided for review.

## Project guide

- [Getting started and CI downloads](docs/GETTING_STARTED.md)
- [Architecture and interfaces](docs/ARCHITECTURE.md)
- [CLI options and text/JSON export schema](docs/EXPORT_FORMAT.md)
- [Native CLI packaging and clean-checkout verification](docs/PACKAGING.md)
- [Ali's Task 1 evidence and handoff](docs/ALI_TASK_1.md)
- [Ali's Task 2 integration and evidence](docs/ALI_TASK_2.md)
- [Ali's Task 3 packaging and acceptance](docs/ALI_TASK_3.md)
- [Ali's Task 4 release candidate](docs/ALI_TASK_4.md)
- [Project report](docs/PROJECT_REPORT.md) and [demo script](docs/DEMO.md)
- [Historical bug example](docs/BUG_TRACING_EXAMPLE.md)
- [Task 1–4 milestones and ownership](docs/PLAN.md)
- [Dataset and evaluation protocol](docs/DATASET.md)
- [Decisions and professor questions](docs/DECISIONS.md)
- [Contribution workflow](CONTRIBUTING.md)
- [Trello board](https://trello.com/b/6kk3pY0g/line-tracker)

## Branch workflow

Use `development` for shared integration. Branch each task from development and open its pull request back into development. Promote reviewed, tested code to `main` through a separate release pull request. See CONTRIBUTING.md for the workflow.

## Continuous integration

The workflow builds on Ubuntu, Windows and macOS using pinned Temurin 17.0.21+9 and Python 3.12. Each runner performs Maven verification, builds a native runtime bundle, tests its extracted CLI and actual JavaFX workflow, and uploads artifacts. The aggregate `test` check requires all three platforms. Use the run for the exact candidate commit when assessing a package. Artifacts have 14-day retention. `./mvnw spotless:apply` (`.\mvnw.cmd spotless:apply` on Windows) fixes Java formatting.
