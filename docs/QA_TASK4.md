# Mujtaba regression audit — preliminary, November 14

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
