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

The foundation and integration PRs remain subject to peer review. Until merged, use the relevant task branch explicitly: `ali/task-2-exports` for advanced matching/exports or `ali/task-3-package` for splits/native packaging. Do not assume main/development contains unmerged files. For a reproducible build, check out the exact reviewed commit as described in [PACKAGING.md](PACKAGING.md).

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

## Submit work for review

```text
git add path/to/changed/files
git commit -m "Describe the behavior changed"
git push -u origin yourname/task-1-description
```

Open a GitHub pull request with development as the target. Fill in the PR template, link the Trello card, and wait for another teammate's approval and passing CI. Resolve feedback and merge only after those requirements are met. Do not request review from an account that cannot access the repository. If development advances, update your branch and recheck it before merging.

Task branches flow into development. A separate reviewed release PR promotes development into main. Follow the team's one-approval and `test`-check policy; the workflow's aggregate check now requires successful Windows, Linux and macOS builds including native-package tests. Main is for reviewed stable releases; do not push directly to it.

## CI downloads and dependencies

Open the pull request's Checks tab or the repository Actions tab and select the Java build run for the desired commit. Artifacts named `line-tracker-native-<OS>-<ARCH>` contain the tested native archive with a bundled Java runtime. Choose your OS/CPU architecture and extract the archive before using its launcher; [PACKAGING.md](PACKAGING.md) gives build and run details. `line-tracker-<runner>` artifacts contain the standalone JAR, which still needs Java 17. Test reports are uploaded separately; artifacts expire after 14 days. The application currently provides CLI functionality only.

Dependabot configuration proposes weekly Maven and GitHub Actions updates targeting development after the configuration exists on the default branch. Dependency updates require the same review and CI; they are not automatically merged. When the first reviewed release reaches main, verify Dependabot is active in the repository's dependency settings.

The JavaFX UI and final evaluation dataset remain separate tasks. Baseline, advanced and split matching are integrated on the packaging branch. See ARCHITECTURE.md for the shared interface and current limitations.
