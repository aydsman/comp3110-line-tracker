# Evaluation datasets

`new/manifest.json` contains 25 real source pairs: 20 development cases and five reserved test projects. Original source bytes, release identities, licenses and checksums are retained. `development/manifest.json` contains seven separate synthetic controls; they do not count toward the 25 real files. See [review scope](REVIEW.md), [fixture identities](development/README.md) and [annotation schema](../docs/DATASET.md).

The first five real pairs and five original QA fixtures retain their recorded source-review evidence. The remaining 20 real pairs and split fixture reference the November 20 **AI-assisted source review by Codex for Ayden**. The extra foundation fixture has no separate annotation-review claim. Source review does not grant release approval or establish an independently approved dataset freeze. Reserved project labels must remain outside parameter tuning.

Run `python3 validate_dataset.py` from the repository root for structural checks. After building the candidate JAR, run the combined CLI and evaluation matrix:

```sh
python3 scripts/verify_release.py --jar target/line-tracker.jar \
  --java "$JAVA_HOME/bin/java" --code-revision "$(git rev-parse HEAD)"
```

The runner executes the 13 evaluator tests with real Java integration enabled, validates 32 cases, audits 71 CLI checks, verifies actual CLI defaults and evaluates six baseline/advanced profiles across development, reserved test and synthetic data. Counts are required checks, not a claim that a particular candidate passed. Raw logs and JSON reports go to a new ignored `target/release-qa` directory; use `--output target/release-qa-next` for another run. Run its orchestration regression tests with `python3 -m unittest discover -s scripts -p test_verify_release.py -v`.

The existing Eclipse benchmark archive is not committed. To rerun its two default-guard profiles and two explicit 8,000,000-cell profiles, add `--existing-zip /path/to/EvaluationDataset.zip`. Without that file, available checks still run, `summary.json` records incomplete benchmark coverage, and the runner exits **3**. Exit **1** means a check failed, **2** means invalid inputs, and **0** means the requested full QA matrix completed. A documented default memory-guard failure remains visible in its partial report; the larger profile must complete. Completion does not mean perfect accuracy, GUI acceptance, native-package acceptance or peer approval. Historical benchmark reports are not a fresh run of the candidate.
