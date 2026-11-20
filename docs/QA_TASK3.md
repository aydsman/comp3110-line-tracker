# Mujtaba Task 3 evaluation handoff — November 14, 2026

The new collection contains 25 distinct real source files with old/new revisions, licenses, provenance, source checksums and draft line mappings. There are 20 development cases and five reserved test projects. Six synthetic fixtures are separate and do not count toward 25. The ten added Requests files range from 34 to 1,094 lines; auth and structures are explicit unchanged controls. Requests already belonged to development, so all its new files remain there. This creates a project/size imbalance; results should not be generalized from pooled scores alone.

Annotations were selected from source inspection before executing the project matcher. The 15 real split groups include pure wraps, splits with edits/comment extraction and groups longer than the implementation's four-fragment limit. The current schema cannot represent many-to-one merges: principal old statements retain modified identities, with superseded syntax lines deleted. Per-case annotation notes document these decisions for peer review. No predictions were used as reference answers and no matcher thresholds were tuned.

Ayden's [November 11 review](https://github.com/aydsman/comp3110-line-tracker/pull/2#pullrequestreview-5861569801) is recorded for the first five real pairs and five original fixtures at their reviewed revision. Other pairs and the new split fixture await independent review. This is an evaluation of a saved draft snapshot, not a final reviewed dataset freeze or GitHub approval.

## New dataset results

Executed Ayden's nominated split backend at `211a804efea0f360be99148148ae963c4c5fbc5c`, Java 21 on Windows, with its default 4,000,000-cell resource guard. Advanced defaults remain candidate limit 15, context radius 4, minimum score 0.65 and maximum four split fragments. Dataset snapshot: `43f8e24` (full revision available in Git). Every case completed in both modes.

| Partition | Mode | Correct / reference pairs | Precision | Recall | Pair F1 | Exact split groups |
| --- | --- | --- | --- | --- | --- | --- |
| Development, 20 real files | Baseline | 4660 / 4912 | 99.87% | 94.87% | 97.31% | 0 / 15 |
| Development, 20 real files | Advanced | 4839 / 4912 | 99.79% | 98.51% | 99.15% | 5 / 15 |
| Reserved test, 5 real files | Baseline | 57 / 82 | 100% | 69.51% | 82.01% | No reference splits |
| Reserved test, 5 real files | Advanced | 74 / 82 | 100% | 90.24% | 94.87% | No reference splits |
| Six synthetic fixtures | Baseline | 5 / 7 | 100% | 71.43% | 83.33% | 0 / 1 |
| Six synthetic fixtures | Advanced | 7 / 7 | 100% | 100% | 100% | 1 / 1 |

See `docs/qa/task3-{baseline,advanced}-{development,test,fixtures}.json` for per-case predictions, missing/unexpected pairs, runtime and source/annotation/binary/settings hashes. Runtime includes JVM startup. Large mostly unchanged files dominate pooled development scores; the two unchanged controls and many unchanged lines must not be mistaken for broad edited-line accuracy. Advanced real split accuracy is 33.33%, despite its high pair F1. Edited splits, five/six-fragment groups and anchor ownership explain important unsupported cases; inspect the recorded failures before proposing changes. Gold labels remain fixed independently of those predictions.

## Existing Eclipse benchmark

Mujtaba supplied `EvaluationDataset.zip` locally on November 14. It contains 23 XML reference sets and 28 original/version comparisons, including six BaseTypes versions. References label selected original lines only. `evaluate_existing.py` scores predictions on those original lines, respects explicit `NEW=-1` deletion labels and preserves one-to-many references. Unlabeled original lines are excluded rather than invented as negatives. The archive's DoubleCache reference has `CHECKED=FALSE`; the reports retain this status and also provide checked-reference-only pooled pair metrics.

| Resource profile | Mode | Completed comparisons | Correct / reference pairs on completed cases | Pair F1 | Exact labeled-old-line accuracy |
| --- | --- | --- | --- | --- | --- |
| Default 4m, backend `211a804` | Baseline | 26 / 28 | 42 / 234 | 30.32% | 81 / 273 (29.67%) |
| Default 4m, backend `211a804` | Advanced | 26 / 28 | 197 / 234 | 87.17% | 226 / 273 (82.78%) |
| Explicit 8m, integration `2cab3a9` | Baseline | 28 / 28 | 50 / 253 | 32.89% | 95 / 298 (31.88%) |
| Explicit 8m, integration `2cab3a9` | Advanced | 28 / 28 | 216 / 253 | 87.63% | 248 / 298 (83.22%) |

Default runs are incomplete: DeltaProcessor needs 4,279,920 LCS cells and GC needs 4,797,910. Both errors remain in the reports. The separate 8m run uses Ali's CLI configuration support at `2cab3a9ce64a02a1119653ba4c9aa6578d111222`. Only the resource allowance increases; candidate/score/context thresholds remain fixed. This profile completed all cases, and its code revision and guard are explicitly different from the default profile. Do not compare successful-only default denominators with the complete run as if they covered the same lines.

See `docs/qa/existing-{baseline,advanced}.json` and `existing-{baseline,advanced}-8m.json`. Pair F1 and exact labeled-line accuracy have different definitions. Neither reproduces historical slide percentages for the original LHDiff implementation. The archive, original source files, notes and XML remain local because redistribution rights have not been established; the repository contains the adapter and numerical evidence only.

## Reproduce

Build each named revision with its Maven wrapper `clean verify`. With Python and Java on PATH, run from the QA repository root (substitute the actual JAR path):

```powershell
python -m unittest -v test_evaluate_dataset test_evaluate_existing
python validate_dataset.py
python evaluate_dataset.py datasets/new/manifest.json --jar PATH_TO_JAR --code-revision 211a804efea0f360be99148148ae963c4c5fbc5c --settings '{"max_lcs_cells":4000000,"candidate_limit":15,"context_radius":4,"minimum_score":0.65,"max_split_fragments":4}' --advanced --split development --output results/new-advanced-development.json
python evaluate_existing.py ../EvaluationDataset.zip --jar PATH_TO_INTEGRATION_JAR --code-revision 2cab3a9ce64a02a1119653ba4c9aa6578d111222 --advanced --max-lcs-cells 8000000 --output results/existing-advanced-8m.json
```

Omit `--advanced` for baseline, use `--split test` for the reserved new partition, and use `datasets/development/manifest.json` without a split filter for fixtures. Omit `--max-lcs-cells` to reproduce the default benchmark profile, including its exit code 1 for incomplete execution. Pass `--java` if the required runtime is absent from PATH. Set `LINE_TRACKER_TEST_JAR` to the split-backend JAR and `LINE_TRACKER_TEST_JAVA` to Java 17+ before unit tests to enable the real end-to-end split test; it otherwise reports a skip. The November 14 run enabled it: all 13 tests passed.

## Review required

Independent review is still needed for 20 real pairs and the new split fixture, plus explicit agreement on composite split/merge conventions. Reconcile PR #2, #3 and this dependent branch through reviewed development integration; do not bypass required approvals/checks. The different two-line QA versus three-line foundation `identical` fixture must be resolved deliberately during integration. Rerun evaluation on the eventual reviewed release candidate. See [Task 4 audit](QA_TASK4.md) for the remaining release checks.
