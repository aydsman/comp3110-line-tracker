# Ali Task 1 — baseline integration handoff

This is the Task 1 record at revision `a2c5f16679cc5d5fea508fbcb42e9bee52837dff`. Later advanced integration and exports are recorded in [Task 2](ALI_TASK_2.md).

Task: [Define shared contracts and integrate the baseline](https://trello.com/c/NY0coQuT). This work builds on Ayden's Java foundation in [pull request #1](https://github.com/aydsman/comp3110-line-tracker/pull/1). That foundation is an unmerged dependency; its Maven setup, models, reader, baseline algorithm, original pipeline/CLI and tests remain Ayden's contribution. Ali owns the integration changes described below.

## Scope and public handoff

The baseline remains accessible with `java -jar target/line-tracker.jar map OLD NEW`. Standard output contains only sorted `old-new` pairs; unmatched lists and failures go to standard error. Exit codes remain 0 for success, 1 for comparison/file failure, and 2 for incorrect usage. JSON, advanced matching, the GUI and final evaluation are separate tasks.

All public types are in `edu.comp3110.linetracker`:

| API | Consumer contract |
| --- | --- |
| `SourceLine(int number, String original, String normalized)` | Original one-based number and display text are retained; only normalized text is compared. |
| `SourceFile(String path, List<SourceLine> lines)` | Immutable lines have consecutive numbers starting at 1. `fromLines` creates comparison copies. |
| `LineMatcher.match(SourceFile, SourceFile, MatchConfig)` | Backend returns matches and all unmatched original lines using the requested config. |
| `MappingPipeline(LineMatcher)` | Constructor injection selects the backend without coupling callers to its algorithm. |
| `compare(Path, Path, MatchConfig)` | Reads UTF-8 inputs, then delegates to the loaded-source overload. |
| `compare(SourceFile, SourceFile, MatchConfig)` | Compares existing source snapshots and validates backend output before returning. |
| `Match(List<Integer> oldLines, List<Integer> newLines, MatchKind kind, double score)` | One old line maps to one new line, or multiple consecutive new lines for SPLIT. Finite score is in [0,1]. |
| `MappingResult(List<Match> matches, List<Integer> deleted, List<Integer> added, MatchConfig config)` | Every original line belongs to exactly one match group or unmatched list. Config must equal the request. |
| `TextMappingFormatter.format(MappingResult)` | Returns canonical pairs sorted by old then new number with a trailing `\n` per pair; empty matches produce an empty string. |

Null inputs are rejected before backend work. The pipeline also rejects null backend results, different result config, missing coverage, reused lines and out-of-bounds mappings. Path read failures identify the old/new input, UTF-8 encoding and path in an `IOException` whose cause preserves the original failure. The loaded-source overload has no file I/O. Direct `SourceReader` callers handle their own `IOException` and input-role UI messaging.

For Aadi, load source files and compare them in a JavaFX background task. Keep those records for the original-text panels, and pass them to the loaded-source overload so the displayed text and compared text share one snapshot. Clear stale results after input changes or failure. Export the returned result through the shared formatter, and highlight every target line of a SPLIT. Scores describe similarity and are not probabilities.

For Ayden, retain the `LineMatcher` interface and constructor-injected pipeline. Task 2's separate branch preserves those shapes and the one-argument `MatchConfig` constructor while adding settings. Return the effective requested config and complete exclusive coverage; both pipeline overloads enforce the same rule. Advanced backend integration requires a fresh test run after combining branches.

## Acceptance and evidence

| Task 1 requirement | Evidence in this branch | Remaining acceptance |
| --- | --- | --- |
| Java 17/Maven clean build | Java 17 clean verification passed: 21 unit tests and 6 packaged-JAR integration tests | CI results must match the submitted revision; check the PR for platform results. |
| File loading and normalization | UTF-8 reader preserves original text/numbering; Unicode boundary whitespace is consistently removed from comparison text; path failures identify input role | Teammate review of behavior and limitations. |
| Shared models, split groups, scores, config and errors | Concrete public contracts above and in [architecture](ARCHITECTURE.md); pipeline validates results and requested config | Explicit team agreement is still outstanding. |
| Real baseline and basic output | Packaged CLI tests passed; real Requests source-file smoke check passed, as recorded below | JavaFX handoff is separate. |
| Current ownership | [Architecture](ARCHITECTURE.md) and [work plan](PLAN.md) assign Ayden backend, Aadi frontend, Ali integration and Mujtaba QA | Another teammate reviews the integration PR. |
| Independent QA fixtures | This branch retains Ayden's two synthetic development pairs | Mujtaba's draft fixtures require independent review and deliberate conflict resolution. |
| Rubric, existing dataset and mandatory bug tracing | Questions retained in [decisions](DECISIONS.md) | Unconfirmed as of Nov 11, 2026; no official source or dataset access has been supplied. |
| Frontend baseline comparison | Loaded-source API and export formatter are available for Aadi | Aadi's actual JavaFX comparison and GUI/CLI equivalence have not been verified. |

Implementation evidence supports review of Ali's changes. It does not establish the missing instructor decisions, team agreement, reviewed gold labels, frontend demonstration or peer approval required before Done.

## Verification record

From the repository root:

```sh
./mvnw clean verify
java -jar target/line-tracker.jar map datasets/development/insert-delete/old.txt datasets/development/insert-delete/new.txt
```

On Windows use `.\mvnw.cmd clean verify`. `verify` runs formatting, unit tests, packaging and Maven Failsafe integration tests against the actual JAR. The `package` phase alone stops before the packaged-JAR integration tests. Reports are under `target/surefire-reports` and `target/failsafe-reports`.

The fixture's expected stdout is `1-2\n3-3\n`; stderr reports unmatched old `[2]` and new `[1, 4]`. Pipeline tests should cover loaded/path equivalence, requested config, invalid backend output, missing/invalid input handling and original numbering. Formatter tests should cover sorted pairs, split expansion and empty output. Normalization checks should include boundary non-breaking spaces without changing original text. The packaged CLI checks confirm process exit codes and stream contents rather than calling `Main.run` alone.

Verified locally on Nov 11, 2026 with Eclipse Temurin 17.0.21+9 and the pinned Maven 3.9.9 wrapper. `./mvnw spotless:apply clean verify` passed: 21 unit tests (12 baseline, 6 integration contract, 3 formatter) and 6 packaged-JAR integration tests, with no failures, errors or skips. The integration suite launched the actual executable eight times. Cross-platform results for the submitted revision are recorded by the PR's CI checks.

An isolated source snapshot of Ayden's Task 2 commit `82322b420d89ccdd8138bd693336e2fadec2081a`, overlaid with this pipeline, normalizer, formatter and their two unit-test classes, passed `spotless:apply clean verify`: 28 tests, no failures/errors/skips. This includes the advanced matcher through the injected pipeline. It checks source/API compatibility without merging Task 2 here. Task 2 still formats inline; when wiring the shared LF formatter into its CLI, update its existing `System.lineSeparator()` output expectation to `"\n"` for Windows.

The packaged baseline also ran against Requests `src/requests/__version__.py`, v2.32.3 to v2.32.4, extracted from Mujtaba's QA commit `0d28b349f3d243a4fa5aed32ab951d78f300b2f9` (`datasets/new/requests-version`). Both input hashes matched that branch's Apache-2.0 provenance manifest. The run exited 0 with 12 sorted mappings across 14 old/new lines and unmatched lines `[8, 9]` on each side (version/build constants). Bounds, uniqueness, normalized equality and complete coverage were checked independently. Draft expected labels were not used; this smoke check establishes executable behavior, not benchmark accuracy or independent label approval.

## Fixture integration and baseline limits

Ayden's `datasets/development/identical` uses three original lines, including a blank line. His `BaselineTest` expects three matches. Mujtaba's Task 1 branch uses the same paths for a different two-line `alpha`/`beta` example with two expected matches. Do not overwrite only the inputs or expected JSON. Preserve the matching fixture/tests together or retain both under distinct case IDs, then run structural validation and the relevant suites. The insert/delete mappings agree across those branches. The QA manifests still name no independent reviewer.

The baseline only matches normalized equality in order. Lowercasing and whitespace collapse can hide semantic changes even inside literals; UNCHANGED is not proof of identical source behavior. Unmatched lines remain candidates for later edited/moved/split matching. LCS uses quadratic time/memory with a default four-million-cell guard. Levenshtein similarity operates on Java UTF-16 code units. Synthetic fixtures and draft real-data evaluations do not establish final accuracy, and the existing course benchmark remains unavailable.
