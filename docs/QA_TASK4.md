# Mujtaba regression audit — November 19 update

## Current release decision — blocked pending team handoffs

On November 19, Mujtaba's available QA work was rerun against the verified Windows CI package from [Ali's workflow run](https://github.com/aydsman/comp3110-line-tracker/actions/runs/43065413938). The downloaded Actions artifact SHA-256 matched GitHub's published digest. Package metadata identifies clean CI test-merge commit `8abe07f2b1759c585fe484b1277b87745aeabfe7`, whose parents are development `c8664a89cf378c01d0dadb3c33c711d9621e132f` and Ali's feature branch `2cab3a9ce64a02a1119653ba4c9aa6578d111222`. This is explicitly a feature-branch test package, not a nominated final integrated release.

Fresh results, using the package's included Temurin Java 17.0.21+9 runtime:

- All 9 packaged launcher checks passed with JAVA_HOME removed and no Java on PATH, including spaced extraction paths, baseline/advanced mappings, split groups, JSON parsing, exports and error exits.
- All 13 evaluator tests passed, including the real Java split integration test.
- All 25 real cases and six synthetic fixtures passed structural validation; source and reference answers were preserved.
- All 69 integrated CLI text/JSON, coverage, input-preservation, export and error checks passed.
- Both modes completed all 31 new/fixture comparisons. The unchanged reserved test set retained baseline pair F1 82.01% and advanced 94.87%. Advanced exact real split accuracy remains 5/15; the synthetic split passes.
- Existing benchmark: both default-guard runs visibly retain the same two resource failures. Separately declared 8m resource profiles complete all 28 comparisons, with baseline pair F1 32.89% and advanced 87.63%. Similarity thresholds and all reference labels were unchanged.

The full fresh evidence is in `docs/qa/nov19/`, including package provenance, CLI audit, test logs, all per-case numerical reports, JAR and source/annotation hashes. This closes the earlier local Windows CLI-package verification gap. It does not establish final GUI behavior or local Linux/macOS archive acceptance. Report timestamps are UTC; the QA run and Trello deadline record are dated November 19 in America/New_York.

### Required handoffs before final sign-off

1. Ali supplies the nominated integrated release-candidate commit and Windows package, with GUI/backend/export features and chosen resource profile identified. GitHub had no releases and development/main still pointed to the planning scaffold when checked today.
2. Ayden independently reviews the remaining 20 real-file mappings and the new split fixture and confirms composite split/merge and duplicate-line conventions. The first five real source reviews remain credited to their actual reviewed revision. No approval is inferred from validation or test scores.
3. The team completes required dependency integration, approvals and CI. QA PRs #2, #3 and #11 were still draft and blocked, with no check runs on their heads when checked. Adding a fake success status is outside this QA handoff.
4. Mujtaba checks actual final GUI/CLI equivalence, final exports/file errors/splits, and reruns the matrix if the nominated release differs from the tested build. Historical bug-tracing acceptance, if required, also needs a testable team handoff.

The required written blocker status was [posted to Mujtaba's Task 4 card](https://trello.com/c/V5fRlOGk/16-task-4-run-final-regression-and-audit-evaluation-results-mujtaba#comment-6affd31dca0805f5ceba1f8b) before its November 19 11:59 PM deadline. Ali and Ayden were tagged there for the release and review handoffs. Keep final release sign-off pending; no teammate implementation branch was changed. Recheck final fixes before submission.

## Historical November 14 audit

The following records describe the earlier test run. Today's package verification and handoff status above supersede the earlier package limitation.

This is QA evidence for feature branches, not final release sign-off. No teammate implementation branch was changed.

| Test subject | Exact revision | Result |
| --- | --- | --- |
| Ayden split backend | `211a804efea0f360be99148148ae963c4c5fbc5c` | Clean Maven verify: 25 tests pass |
| Ali integration / exports | `2cab3a9ce64a02a1119653ba4c9aa6578d111222` | Clean Maven verify: 47 unit tests pass, 1 symbolic-link test skipped because Windows disallowed link creation; all 10 packaged-JAR tests pass |
| Aadi frontend | `d87d48d32327851a3232406810e612d157ccc468` | Clean Maven verify: 21 unit and 6 packaged-CLI tests pass |
| Mujtaba Python evaluators | This branch | All 13 tests pass, including real advanced split CLI integration |
| Integrated CLI regression audit | Ali revision above | All 69 checks pass |

`audit_cli.py` runs baseline and advanced text/JSON comparisons on all 25 real cases and six fixtures, verifies identical pair sets, unmatched-line coverage and preserved inputs, and checks spaced-path export, refusal to overwrite an existing export or input, missing-file errors, invalid options/thresholds and the LCS guard. Evidence: `docs/qa/cli-regression-audit.json`. Run `python audit_cli.py --jar PATH_TO_INTEGRATION_JAR --code-revision 2cab3a9ce64a02a1119653ba4c9aa6578d111222 --output results/cli-audit.json` with a Java 17+ runtime, or supply `--java`.

The available JavaFX branch calls the baseline pipeline and has no advanced-mode/export controls yet. A GUI launch was attempted; its window appeared briefly but could not be reliably bound for interaction, so no manual GUI result/highlight/error equivalence is claimed. CLI text/JSON equivalence does not substitute for GUI verification. No frontend code was changed.

Native runtime packaging has not been verified in this QA run: the installed JDK is 21, while the package script requires a full JDK 17 and the team pins Temurin 17.0.21+9. The official Adoptium metadata request returned HTTP 403, so no alternate unverified runtime was installed. Existing local Java/JAR tests do not establish Windows, Linux or macOS archive acceptance.

## Findings and follow-up

| Finding | Evidence / current state | Needed before sign-off |
| --- | --- | --- |
| Evaluator report can overwrite an unselected partition's answers | Ayden's PR #3 review; fixed in `77f71b1`, protected paths now collected before filtering. Regression checks both directions and old/new/answer/license files. This branch also protects the added new-revision license field. | Teammate review of fix |
| Default memory guard rejects two existing benchmark comparisons | `existing-*.json`: DeltaProcessor 4,279,920 cells; GC 4,797,910 cells. Explicit 8m CLI profile completes all 28 comparisons. | Team chooses/document release resource profile, preserves explicit failure behavior |
| Split limits cause misses | Advanced recovers 5/15 draft real split groups; all targets required for exact group accuracy. Synthetic pure split passes end-to-end. | Review annotations and distinguish unsupported edited/long splits from bugs |
| Dataset review/integration incomplete | Five real pairs have recorded source review; 20 remain pending. PRs #2 and #3 are unmerged. | Actual independent review, dependency integration, CI and approvals |
| Final GUI/release unavailable | Frontend and newer backend/exports remain separate branches. | Release candidate, actual GUI/CLI equivalence, exports/errors/splits and archive checks |
| Historical bug-introducing-change feature | Course scope/acceptance remains a team/professor question. The evaluator defect above is a QA bug report, not implementation of historical bug tracing. | Ayden/Ali clarify implementation handoff; Mujtaba tests it if required |

Task 4 is due November 19. Keep it In progress with explicit blockers until the release candidate exists. Recheck any final fixes and numerical claims before Ali's November 20 submission. This record supplies the requested preliminary written blockers and does not mark Task 4 complete.
