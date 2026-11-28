# Getting started

## Requirements

Install Temurin JDK 17.0.21+9 (the CI version), Git, and an editor such as VS Code or IntelliJ. Set `JAVA_HOME` to the JDK directory. The Maven wrapper downloads Maven 3.9.9 and verifies its pinned checksum on first use; an internet connection is needed for the first build and dependency downloads. You do not need a separate Maven installation. Python 3.12 or newer is needed only to create/test native packages; the finished native packages need none of these development tools.

## Get the shared project

```text
git clone https://github.com/aydsman/comp3110-line-tracker.git
cd comp3110-line-tracker
git switch development
git pull --ff-only
```

For the integrated desktop/CLI candidate, use `git switch ali/task-4-release` after cloning, then check out its exact reviewed commit. Development contains only merged work. The original integration PRs and new candidate remain subject to peer review. See [PACKAGING.md](PACKAGING.md) for reproducible builds and [ALI_TASK_4.md](ALI_TASK_4.md) for acceptance status.

## Create your task branch

Start from up-to-date development with a clean working directory:

```text
git switch development
git pull --ff-only
git switch -c yourname/task-1-description
```

Use your real name and task description. Keep one focused task per branch. For example, Aadi can use `aadi/task-1-frontend`.

## Build, format and test

Windows PowerShell:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd --batch-mode clean verify
java -jar target/line-tracker.jar map datasets/development/insert-delete/old.txt datasets/development/insert-delete/new.txt
```

macOS/Linux:

```sh
./mvnw spotless:apply
./mvnw --batch-mode clean verify
java -jar target/line-tracker.jar map datasets/development/insert-delete/old.txt datasets/development/insert-delete/new.txt
```

`spotless:apply` fixes Java formatting. `verify` checks formatting first, runs JUnit tests, builds the runnable JAR and tests that executable through Failsafe. It fails rather than silently reformatting files. The example prints `1-2` and `3-3`; unmatched old/new line numbers go to standard error. Add `--advanced --format json` for advanced prediction groups, or `--output FILE` to create a new UTF-8 export. See [EXPORT_FORMAT.md](EXPORT_FORMAT.md).

## Run the JavaFX comparison shell

On a desktop with JDK 17, run `./mvnw javafx:run` (`.\mvnw.cmd javafx:run` on Windows). Choose the old and new UTF-8 files, choose Baseline or Advanced mode, and click **Compare**. The side-by-side panes show original text with one-based line numbers. Select a mapping to highlight all of its old/new lines, including every split fragment. Text/JSON exports use the shared CLI format and refuse existing files. Changing inputs or settings clears stale output. The explicit benchmark profile raises the memory guard to eight million cells without changing similarity thresholds.

For a quick check, choose `datasets/development/insert-delete/old.txt` and `datasets/development/insert-delete/new.txt`. Compare should show mappings `1 → 2` and `3 → 3`, one unmatched old line, and two unmatched new lines. Select `1 → 2` to highlight `alpha` on old line 1 and new line 2. Select a different mapping to move both highlights. Choosing a different file clears the previous comparison.

## Submit work for review

```text
git add path/to/changed/files
git commit -m "Describe the behavior changed"
git push -u origin yourname/task-1-description
```

Open a GitHub pull request with development as the target. Fill in the PR template, link the Trello card, and wait for another teammate's approval and passing CI. Resolve feedback and merge only after those requirements are met. Do not request review from an account that cannot access the repository. If development advances, update your branch and recheck it before merging.

Task branches flow into development. A separate reviewed release PR promotes development into main. Follow the team's one-approval and `test`-check policy; the workflow's aggregate check now requires successful Windows, Linux and macOS builds including native-package tests. Main is for reviewed stable releases; do not push directly to it.

## CI downloads and dependencies

Open the pull request's Checks tab or Actions and select the Java build run for the desired commit. Artifacts named `line-tracker-native-<OS>-<ARCH>` contain the tested desktop/CLI archive with its bundled runtime. Choose your OS/CPU architecture and extract it before using its launcher; [PACKAGING.md](PACKAGING.md) gives details. The standalone JAR still needs Java 17 for CLI use; desktop use additionally needs the pinned JavaFX libraries. Test reports are uploaded separately and artifacts expire after 14 days.

Dependabot configuration proposes weekly Maven and GitHub Actions updates targeting development after the configuration exists on the default branch. Dependency updates require the same review and CI; they are not automatically merged. When the first reviewed release reaches main, verify Dependabot is active in the repository's dependency settings.

The release-candidate branch combines the JavaFX shell with baseline, advanced and split matching. Final desktop controls, packaging and release QA are being verified together. See ARCHITECTURE.md for the shared interface and current limitations.
