# Ali Task 3 — split integration and native CLI packages

Task: [Package the application and complete required bug tracing](https://trello.com/c/ctTqMFgf). This work builds on [Ali's Task 2 PR #7](https://github.com/aydsman/comp3110-line-tracker/pull/7) and integrates [Ayden's split-detection PR #5](https://github.com/aydsman/comp3110-line-tracker/pull/5), preserving both histories. These dependencies require review before integration into `development`.

## Delivered implementation

Advanced matching now includes Ayden's exact reconstruction of one original line from 2–4 consecutive available nonblank lines. Exact moves keep priority; splits consume their complete group before fuzzy edits. Shared text export emits every pair, while JSON retains one `split` group. Original bounds, complete coverage and exclusive ownership remain enforced by the existing pipeline.

`scripts/package.py` creates an OS/architecture-specific CLI archive with its own Java 17 runtime, application JAR, launchers, synthetic examples, usage instructions and runtime legal notices. `build-info.json` records the exact source commit, dirty state, JDK version/vendor, runtime platform/architecture and JAR SHA-256. The launcher locates its bundled runtime relative to itself and forwards arguments and exit codes. A standalone JAR remains available for users with Java installed.

The Maven wrapper pins Maven 3.9.9 and its distribution checksum. Build-lifecycle plugins are explicitly pinned; JAR entry timestamps are fixed. CI uses Temurin 17.0.21+9 and Python 3.12 on Windows, Linux and macOS. Each platform builds, extracts and tests its own native archive before uploading it. Native archives include build-time metadata, so byte-for-byte archive reproducibility is not claimed.

See [clean-checkout and package instructions](PACKAGING.md) and [getting started](GETTING_STARTED.md). The packages currently provide CLI functionality; adding JavaFX will require revisiting runtime modules and verifying the real frontend.

## Verification and acceptance

Local macOS ARM64 verification on Nov 11, 2026 passed 48 unit tests and 10 packaged-JAR integration tests, with zero failures/errors/skips. The native archive passed nine launcher checks after extraction into a path with spaces and invocation from another working directory, with `JAVA_HOME` removed and no Java on `PATH`. Checks covered baseline/advanced/split results, independent JSON parsing, UTF-8 file exports, usage and missing-input exits, JAR checksum and runtime legal notices. The PR records the submitted commit's clean-checkout and platform CI evidence.

| Acceptance item | Current evidence | Still required |
| --- | --- | --- |
| Runnable platform package | Native runtime bundle and extracted-archive smoke checks | Teammate build/run confirmation on the chosen delivery platform |
| Reproducible source setup | Pinned wrapper/plugins/CI JDK, clean-checkout commands and commit metadata | Reviewed release commit and dependency integration |
| Available backend features | Baseline, edits, moves and exact splits integrated with shared exports | Backend review and independent agreement on nominated evaluation settings |
| Frontend features | Shared loaded-source and formatting APIs remain available | Aadi's actual frontend and end-to-end GUI/CLI equivalence |
| Historical bug tracing, if mandatory | Course requirement remains unconfirmed | Confirm requirement; select and manually verify the historical fix with Ayden if required |
| Peer review | Draft PR and evidence available | Another teammate's approval before Done |

Historical tracing remains pending confirmation. If mandatory, the example must establish the fix and parent revisions, affected pre-fix lines, trace steps, candidate introducing commits and limitations; candidate attribution alone does not prove causation. Final submission release approval belongs to Task 4.
