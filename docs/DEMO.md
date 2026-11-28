# Line Tracker demo

## Ali's short introduction

“Our project compares two versions of a code file and shows which lines correspond. My part connects the backend to the app, adds text and JSON exports, and packages the program so it runs without installing Java. I also check that the desktop and command line produce the same results.”

## Run the packaged application

Extract the package for your operating system and CPU. Launch `line-tracker-gui` on macOS/Linux or `line-tracker-gui.cmd` on Windows. Keep the runtime and app folders beside the launcher. For a source checkout, use the Maven JavaFX run command in [GETTING_STARTED.md](GETTING_STARTED.md).

1. Choose `examples/insert-delete/old.txt` and `examples/insert-delete/new.txt` from the package. In baseline mode, compare them. The mappings are `1 → 2` and `3 → 3`; unmatched old line is 2 and unmatched new lines are 1 and 4. Select a mapping to highlight both corresponding lines.
2. For a split example, use `examples/split-wrap/old.txt` and `new.txt` from the package. Select advanced mode and compare. Old line 1 maps to new lines 1 and 2; old line 2 maps to new line 3. Select the split group and show that both new fragments highlight.
3. Export text and JSON to new filenames. The text contains `1-1`, `1-2` and `2-3`; JSON retains the first two pairs as a single split group. Attempting to export over an existing file should report an error and preserve that file.
4. Select a different input or comparison setting. The old result and export availability should clear. Compare `examples/both-empty/old.txt` and `new.txt` to show the empty state. To demonstrate a readable input error, choose a temporary copy of a source file, rename that copy outside the app, then click Compare.

The equivalent split CLI command is:

```sh
./line-tracker map examples/split-wrap/old.txt examples/split-wrap/new.txt --advanced --format json
```

On Windows, replace `./line-tracker` with `.\line-tracker.cmd`.

## Explain the limits

“Baseline mode finds normalized equal lines. Advanced mode also tries edits, moves and exact splits. It can miss complicated changes, and an unmatched line is not proof that the code was added or deleted. The normal memory limit is four million LCS cells; the explicit benchmark profile allows eight million. We report that setting with the results.”

## Explain verification

“We check the original line numbers, complete coverage, and that no line belongs to conflicting matches. We test the packaged executable and the real desktop controls, compare desktop exports with the CLI, and run the dataset evaluator separately. The package records the exact commit and checksums so QA can repeat the same build.”

For the optional history example, run `scripts/verify_bug_history.py` with a JDK and full Git history. Show the same non-breaking-space input failing before the recorded fix and passing after it. Explain that blame identifies candidates and that a formatting commit can obscure the original operation; do not claim general historical causation from blame alone.
