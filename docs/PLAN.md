# Task 1–4 work plan

Every person has a Task 1, 2, 3 and 4. Trello deadlines are planning deadlines at 23:59 America/Toronto, not evidence of an official course cutoff. Work starts Nov 3, 2026. Final preparation targets Nov 19; submission is Nov 20.

| Section | Window | Ayden — Backend | Aadi — Frontend | Ali — Integration | Mujtaba — QA |
| --- | --- | --- | --- | --- | --- |
| Task 1 | Nov 3–7, due Nov 7 | Baseline LCS matching and content similarity | JavaFX shell, file selection and original line display | Shared models, Maven, input processing and baseline integration | Two tiny fixtures by Nov 4; five reviewed pairs by Nov 7 |
| Task 2 | Nov 8–12, due Nov 12 | SimHash, content/context scoring, conflict resolution | Live pipeline connection, highlights, progress/errors | Advanced pipeline integration, CLI and shared exports | Fifteen reviewed pairs, evaluator, development/test split |
| Task 3 | Nov 13–17, due Nov 17 | Split detection, regressions and settings freeze | Split highlights, export controls and result states | Packaging, clean setup and required bug tracing | Twenty-five reviewed file pairs and existing/new dataset evaluation |
| Task 4 | Nov 18–20 | Critical backend fixes and algorithm explanation, due Nov 19 | UI fixes and reliable demo, due Nov 19 | Release candidate Nov 19; QA-approved submission due Nov 20 | Final regression, results audit and sign-off, due Nov 19 |

## Foundation prepared by Ayden

The initial Maven project, shared records/interface, reader/normalizer, baseline pipeline and CLI, meaningful JUnit tests, CI definition and two synthetic development pairs are implemented on the foundation branch. This overlaps initial setup under Ali's Task 1 to unblock work; Ali remains the integration owner. The fixtures do not replace Mujtaba's independent annotation/review or the final dataset.

Ayden's backend Task 1 foundation is in [pull request #1](https://github.com/aydsman/comp3110-line-tracker/pull/1), which remains an unmerged dependency of Ali's integration branch. Preserve its history and attribution; merge through the team's reviewed development workflow.

## Ali's Task 1 integration

Ali retains the foundation's public models and CLI syntax, adds a pipeline overload for already loaded sources, validates matcher output/config at the integration boundary, gives path-read failures old/new input context, and shares canonical text formatting with future UI callers. The normalization change removes Unicode whitespace at line boundaries consistently. Maven `verify` also launches the packaged JAR for integration checks. See [the Task 1 acceptance and handoff record](ALI_TASK_1.md).

The implementation can be assessed independently of the missing JavaFX frontend. The team-level Nov 7 integration acceptance still requires Aadi's real frontend handoff and another teammate's review. Shared-model agreement, independently reviewed QA fixtures, rubric, existing benchmark and mandatory bug-tracing scope are still outstanding as of Nov 11, 2026. Do not mark the complete end-to-end milestone Done before these gaps are resolved.

## Early handoffs

Ali's Task 2 combines the Task 1 pipeline with Ayden's advanced matcher and adds shared JSON/text exports, CLI settings and safe file output. See [Task 2's handoff](ALI_TASK_2.md). Actual frontend integration, peer review and instructor confirmations remain outstanding.

The original handoff targets were Nov 3 for shared-contract review and professor questions, Nov 4 for independently reviewed tiny fixtures, and Nov 7 for a real pipeline/frontend comparison. These are targets, not evidence that the handoffs happened. Ali coordinates contract changes. Aadi can use sample MappingResult records while developing the UI, then use the loaded-source pipeline overload and shared formatter for real results.

During QA integration, reconcile the same-path `datasets/development/identical` conflict: Ayden's fixture has three lines and a dependent three-match assertion; Mujtaba's branch has a different two-line fixture. Preserve the fixture with its matching expected answers and tests, or keep both under distinct case IDs. Neither branch's draft labels establish independent teammate review.

Task 2 uses baseline outputs while advanced matching is developed, so frontend and evaluator work do not wait for the final matcher. If bug tracing is required, Ayden and Ali begin its design/example in Task 2. Task 3 evaluation uses frozen settings; every change afterward must trigger relevant reruns. Task 4 submission depends on Mujtaba's QA sign-off and the exact final release commit.

## Definition of done

Working implementation, meaningful checks, clear documentation and another teammate's review. All members test their own components. Keep In progress and Review/testing honest; a passing backend test suite is not proof that an unimplemented GUI or research evaluation works.
