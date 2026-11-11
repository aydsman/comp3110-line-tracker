# Ali Task 2 — advanced integration and exports

This record describes Task 2 at `00550df219465dc69ea9c97bea50942bb9b4d9d6`. Later split integration and native packaging are recorded in [Task 3](ALI_TASK_3.md).

Task: [Integrate advanced matching and implement CLI exports](https://trello.com/c/GGsWNjyQ). This branch combines Ali's [Task 1 PR #6](https://github.com/aydsman/comp3110-line-tracker/pull/6) with Ayden's [Task 2 PR #4](https://github.com/aydsman/comp3110-line-tracker/pull/4), retaining both histories. These dependencies still need teammate review and integration into `development`.

## Implemented handoff

The default `map OLD NEW` remains the baseline with canonical text output. `--advanced` injects Ayden's edited/moved-line matcher into the same validating pipeline. `--format json` selects the shared prediction serializer; `--output FILE` creates a new UTF-8 file and refuses existing destinations, including input-file aliases. Options are parsed and validated before any input or output I/O. Usage/configuration failures return 2; comparison/export failures return 1; success returns 0.

The CLI exposes all four current `MatchConfig` fields. Advanced-only settings require `--advanced` so a typo or omitted flag cannot silently ignore the requested matching mode. The actual config is retained in JSON. Matching groups, unmatched lines and all original positions remain subject to pipeline bounds, exclusivity and complete-coverage validation.

For frontend integration, Aadi can use the existing loaded-source pipeline and the exact same formatter:

```java
var config = new MatchConfig(4_000_000, 15, 4, 0.65);
var result = new MappingPipeline(new AdvancedMatcher()).compare(oldSource, newSource, config);
String text = TextMappingFormatter.format(result);
String json = JsonMappingFormatter.format(result);
```

Run file loading, matching and file writing off the JavaFX application thread. Retain the source snapshots for display, clear stale results when inputs/settings change, and show each split as one group. See [export format](EXPORT_FORMAT.md) for schema, stream rules and examples. The shared API/CLI equivalence tests establish a frontend contract; they do not establish that Aadi's actual GUI is connected.

## Acceptance and evidence

| Requirement | Evidence | Remaining acceptance |
| --- | --- | --- |
| Advanced matching integration | Ayden's matcher runs through Ali's validated pipeline; baseline remains default | Review the combined branch and its dependencies |
| Shared text and JSON | Both formatters consume the same result; JSON retains groups, scores, unmatched lines and config | Frontend must call these APIs |
| Safe CLI file exports | UTF-8 new-file creation, no existing destination replacement, errors separated from stdout | Peer review |
| Original bounds and coverage | Pipeline rejects invalid backend coverage/config before formatting | Same contract must be used by UI |
| GUI/CLI equivalence | Direct shared-API/CLI tests and packaged CLI checks | Actual JavaFX comparison and export demonstration unavailable |
| Historical bug-fix example, if required | Requirement still unconfirmed | Obtain course requirement and agree a verified example with Ayden if mandatory |

On Nov 11, 2026, Temurin 17.0.21+9 and the pinned Maven 3.9.9 wrapper passed `spotless:apply clean verify`: 42 unit tests and 10 packaged-JAR integration tests, with no failures, errors or skips locally on macOS ARM64. These include existing advanced matching checks, shared-API/CLI equivalence, malformed-option validation before I/O, custom settings, existing file/symlink/hardlink preservation, help and file failures. An independent Python JSON parser checked eight packaged exports (empty, normalization, edited and moved examples in both modes), matching text pairs, original-line coverage and effective settings. The PR's CI records Windows/Linux results for its submitted revision. No dataset-accuracy or teammate-approval claim is made by this handoff.

## QA integration

Mujtaba's evaluator at `6b6ac77678273b87148dedc620ac910683ebfe25` invokes the unchanged baseline text command. It does not yet consume prediction JSON or select advanced matching. Its gold-label `unchanged` convention requires exact source text, while our `UNCHANGED` represents normalized equality. Predictions must not overwrite gold annotations or be treated as independently reviewed labels. The same-path development-fixture conflict remains documented in [Task 1](ALI_TASK_1.md#fixture-integration-and-baseline-limits).
