# Ali Task 4 — integrated release candidate

Task: [Prepare and verify the submission release](https://trello.com/c/ZzYrHBOL). This work continues [Task 3 packaging](ALI_TASK_3.md) on `ali/task-4-release`.

## Candidate contents

The candidate combines the existing Task 3 backend/CLI package, Aadi's Task 2 JavaFX shell, Mujtaba's Task 3 evaluation tools and dataset, and Ayden's Task 4 explanation/review record. Git merge ancestry preserves their contributions. Integration reconciles the different identity fixtures without deleting or relabeling either example.

The desktop adds advanced-mode selection, explicit resource profiles, complete split highlighting and shared text/JSON exports. Packaging includes a native Java runtime and the pinned JavaFX libraries. The build runs the actual desktop comparison/export smoke workflow alongside CLI checks. [PROJECT_REPORT.md](PROJECT_REPORT.md) and [DEMO.md](DEMO.md) provide report and presentation material; [BUG_TRACING_EXAMPLE.md](BUG_TRACING_EXAMPLE.md) provides a reproducible project-history example.

## Reproduce and identify the build

Follow [PACKAGING.md](PACKAGING.md) for the exact toolchain and platform commands. Check out the reviewed candidate commit, run Maven `clean verify`, then run `scripts/verify_release.py`, package the application, and test the extracted archive. Each archive records its exact commit and checksums in `build-info.json`; the QA summary records the tested JAR hash and code revision. A dirty build is not a final release artifact.

The PR and published CI artifacts identify the candidate commit. Rerun the affected verification whenever source, dependencies, runtime, annotations or settings change. Existing reports for other revisions are retained as historical evidence, not relabeled as this candidate's results.

## Acceptance record

| Requirement | Evidence to use |
| --- | --- |
| Source setup and build | Pinned wrapper/JDK/plugins, Maven verification on clean checkout and native CI |
| CLI/GUI integration | Shared pipeline and formatter tests; actual JavaFX controls and export smoke test |
| Platform package | Bundled-runtime CLI/GUI archive checks on each target runner |
| New dataset | Source hashes, annotation review links, structural checks and separate development/reserved-test results |
| Existing benchmark | Fresh matrix only when the private input archive is supplied; otherwise explicitly missing |
| Report/demo | PROJECT_REPORT.md, ALGORITHM_EXPLANATION.md and DEMO.md |
| Historical example | Exact-snapshot reproduction report and source-history explanation |
| Team acceptance | Separate teammate PR review and Mujtaba's written QA sign-off |
| Submission | Actual destination, course requirements and submission receipt; not inferred from a package build |

## Remaining external acceptance

GitHub currently requires fresh peer review after the foundation merge changed the merge base. This candidate PR is an integration handoff and does not bypass the original reviews or branch protections. Only reviewed, tested code should enter `development`; a separate release PR promotes it to `main`.

The original existing-benchmark archive is not available in this checkout. The November 19 Windows benchmark reports remain useful historical evidence, but the final candidate matrix needs the original archive. The course rubric, official submission destination/cutoff and required scope of historical bug tracing are not confirmed. A report and runnable package do not establish submission or QA sign-off. Keep the task in Review/testing until the actual required evidence is available.
