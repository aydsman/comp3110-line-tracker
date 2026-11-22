# Dataset evaluator

Requires Python 3.9+ and a runnable Java CLI supporting `map OLD NEW` with one `old-new` pair per stdout line. Run one manifest at a time to keep existing/new datasets separate. The evaluator uses the team's annotation validator and never edits expected answers.

From the repository root, after building the matcher JAR, run:

```powershell
python evaluate_dataset.py datasets/new/manifest.json --jar target/line-tracker.jar --code-revision 2f133fc2e52799da717a70449b4a099aedbe30fc --settings '{"max_lcs_cells":4000000}' --split development --output results/new-baseline.json
```

The revision above identifies the baseline used for the first run. Supply the actual build revision and effective settings for each future binary. Use `--java 'C:/Program Files/Java/jdk-21/bin/java.exe'` if Java is absent from PATH. The development base currently lacks the Java build until the baseline PR is integrated. The evaluator itself can be checked with `python -m unittest -v test_evaluate_dataset` and `python validate_dataset.py`.

The manifest is a nonempty JSON array. Each case needs a unique string `id` and relative `old`, `new`, and `expected` paths. Review and development/test fields are retained in the report. Gold JSON follows `docs/DATASET.md`, including complete line coverage. Paths must remain inside the manifest directory. Source line counting uses Java's CR/LF convention, including blank lines and empty files.

Each successful case reports precision, recall, F1, missing/unexpected pairs, inferred added/deleted lines, exact split-group accuracy, unmatched-line classification accuracy, and elapsed time. Pair scores expand a split into its old/new pairs. A split group is correct only when its full target set matches. With no expected splits its accuracy is null. Unmatched classification compares matched/unmatched membership across every original line. With both files empty it is 1.

When both pair sets are empty, precision/recall/F1 are 1. If only one is empty, all three are 0. The report counts empty expected-pair cases separately. Aggregate micro scores pool pair counts across successful files. They are not the average of file scores.

File errors, malformed mappings, nonzero CLI exits, and timeouts are recorded per case. Later cases still run. A failed case has no accuracy score. Any failure makes the report incomplete and returns exit code 1. Exit code 0 means every selected case ran successfully, including cases where the matcher missed mappings. Argument syntax errors return 2.

Reports are replaced atomically and include source/annotation hashes, manifest and JAR hashes, reported code revision/settings, Java/Python versions, platform, and review status. The caller supplies the revision and settings, so these values do not independently prove the JAR's source. Keep the identified binary for reproduction. Times include JVM startup and one run per file, rather than isolated algorithm timing. Text CLI output has no group labels, so split structures and unmatched lines are inferred and match-kind labels are not scored. Pending annotations remain draft evidence.

The Task 3 branch depends on unmerged QA PRs #2 and #3. It contains 25 real pairs (20 development, five reserved test projects) and six separate fixtures. Add `--advanced` to invoke the CLI's advanced mode with frozen defaults. The command template and mode are recorded in the report. Output protection covers all manifest cases before any partition filter, including both revisions' license files.

The committed `docs/qa/task2-*.json` reports preserve the original 15-pair baseline run. See [the Task 3 handoff](QA_TASK3.md) for the updated matrix, remaining annotation reviews, supplied Eclipse archive, sparse-reference adapter, default memory-limit failures and separately declared 8m resource profile. The course ZIP remains local and is evaluated separately with `evaluate_existing.py`.
