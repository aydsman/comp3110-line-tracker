# Getting started

## Requirements

Install JDK 17 (newer JDKs may be used locally, but CI verifies JDK 17), Git, and an editor such as VS Code or IntelliJ. Set JAVA_HOME to the JDK directory if `java -version` does not work. The Maven wrapper downloads Maven 3.9.9 on first use; an internet connection is needed for the first build and dependency downloads. You do not need a separate Maven installation.

## Get the shared project

```text
git clone https://github.com/aydsman/comp3110-line-tracker.git
cd comp3110-line-tracker
git switch development
git pull --ff-only
```

The initial foundation is delivered by PR #1. Until that PR is reviewed and merged into development, the runnable foundation is on `ayden/task-1-backend`. Do not assume main/development already contains unmerged files.

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

`spotless:apply` fixes Java formatting. `verify` checks formatting first, runs JUnit tests and builds the runnable JAR. It fails rather than silently reformatting files. The example prints `1-2` and `3-3`; unmatched old/new line numbers go to standard error.

## Run the JavaFX comparison shell

On a desktop with JDK 17, run `./mvnw javafx:run` (`.\mvnw.cmd javafx:run` on Windows). Choose the old and new UTF-8 files and click **Compare**. The side-by-side panes show the original lines with their 1-based numbers. Below them, the baseline pairs use the same old–new numbering as the CLI and the status shows unmatched counts.

For a quick check, choose `datasets/development/insert-delete/old.txt` and `datasets/development/insert-delete/new.txt`. Compare should show mappings `1 → 2` and `3 → 3`, one unmatched old line, and two unmatched new lines. Select `1 → 2` to highlight `alpha` on old line 1 and new line 2. Select a different mapping to move both highlights. Choosing a different file clears the previous comparison.

## Submit work for review

```text
git add path/to/changed/files
git commit -m "Describe the behavior changed"
git push -u origin yourname/task-1-description
```

Open a GitHub pull request with development as the target. Fill in the PR template, link the Trello card, and wait for another teammate's approval and passing CI. Resolve feedback and merge only after those requirements are met. Do not request review from an account that cannot access the repository. If development advances, update your branch and recheck it before merging.

Task branches flow into development. A separate reviewed release PR promotes development into main. Both branches require one approval and the `test` check, which depends on successful Windows and Linux builds. New commits dismiss stale approvals. Main is for reviewed stable releases; do not push directly to it.

## CI downloads and dependencies

Open the pull request's Checks tab or the repository Actions tab, select the Java build run, and download `line-tracker-ubuntu-latest` or `line-tracker-windows-latest` from Artifacts. Each contains the baseline JAR. Test reports are uploaded separately; artifacts expire after 14 days. The JAR still requires Java and currently provides CLI functionality only.

Dependabot configuration proposes weekly Maven and GitHub Actions updates targeting development after the configuration exists on the default branch. Dependency updates require the same review and CI; they are not automatically merged. When the first reviewed release reaches main, verify Dependabot is active in the repository's dependency settings.

The advanced matcher, further JavaFX work and final evaluation dataset remain separate tasks. See ARCHITECTURE.md for the shared interface before implementing them.
