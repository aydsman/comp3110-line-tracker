# Final release QA — 27 November 2026

Fresh final QA of the nominated integrated release candidate, run by Mujtaba
(AI-assisted) against the exact Windows CI package.

## Tested build

| Item | Value |
| --- | --- |
| Candidate commit | `be967fbde5962df005ef728d4e81eefadef732dd` (`development`, clean) |
| Package | `line-tracker-windows-x86_64-be967fbde596.zip` |
| Package SHA-256 | `e3fcddbf8be7868e1f47443524b09a4e2b8deeebac5ede14c757cc605013d8c2` |
| JAR SHA-256 | `202b9d256b194b33fe1a15b9e350e37337a4933d5a3d4d951f826a4958a5926f` |
| Java runtime | Temurin `17.0.21+9` bundled in the package |
| CI run | [45940106720](https://github.com/aydsman/comp3110-line-tracker/actions/runs/45940106720) (Windows artifact `15172222992`) |
| Existing benchmark | private `EvaluationDataset.zip`, SHA-256 `d29b174a6b381ebf0a86ecd44981224d0da8c57259e7279270132f1cb945e604` |

The downloaded artifact and the inner archive hash match CI's package-smoke
record. The archive is the same clean candidate that passed Windows, Linux,
macOS and aggregate checks.

## Command

```text
python scripts/verify_release.py \
  --jar <package>/app/line-tracker.jar \
  --java <package>/runtime/bin/java.exe \
  --code-revision be967fbde5962df005ef728d4e81eefadef732dd \
  --existing-zip <private>/EvaluationDataset.zip \
  --output target/release-qa-be967fb
```

## Checks

| Check | Result |
| --- | --- |
| Evaluator tests | 13 passed, no skips |
| Structural validation | 32 cases |
| CLI audit | 71/71 checks passed |
| CLI configuration (baseline/advanced) | matches reported settings |
| Input preservation | no changed inputs |

## New dataset (25 real pairs, 7 fixtures)

| Profile | Cases | Precision | Recall | F1 | Exact split groups |
| --- | --- | --- | --- | --- | --- |
| Development — baseline | 20/20 | 99.87% | 94.87% | 97.31% | 0/15 (0.00%) |
| Development — advanced | 20/20 | 99.79% | 98.51% | 99.15% | 5/15 (33.33%) |
| Reserved test — baseline | 5/5 | 100.00% | 69.51% | 82.01% | n/a |
| Reserved test — advanced | 5/5 | 100.00% | 90.24% | 94.87% | n/a |
| Fixtures — baseline | 7/7 | 100.00% | 80.00% | 88.89% | 0/1 (0.00%) |
| Fixtures — advanced | 7/7 | 100.00% | 100.00% | 100.00% | 1/1 (100.00%) |

## Existing benchmark (28 comparisons)

| Profile | Cases | Precision | Recall | F1 | Exact split groups |
| --- | --- | --- | --- | --- | --- |
| Baseline — default 4m | 26/28 | 97.67% | 17.95% | 30.32% | n/a |
| Baseline — explicit 8m | 28/28 | 98.04% | 19.76% | 32.89% | n/a |
| Advanced — default 4m | 26/28 | 90.37% | 84.19% | 87.17% | n/a |
| Advanced — explicit 8m | 28/28 | 90.00% | 85.38% | 87.63% | n/a |

The default 4m profile remains intentionally resource-limited for two cases
(`DeltaProcessor-1-2`, 4,279,920 cells and `GC-1-2`, 4,797,910 cells); their
failures are retained in the default reports and excluded from the partial
scores above. The explicit 8m profile completes all 28 comparisons.

## Status

`scripts/verify_release.py` returned `QA complete` with `changed_inputs: []`.
All 16 steps passed except the two expected default-profile resource limits.

Limitations: this is CLI/package evidence. GUI interaction, native archive
acceptance on Linux/macOS and peer/course acceptance are separate evidence.
The benchmark archive and its source files remain private and are not
redistributed in this directory.
