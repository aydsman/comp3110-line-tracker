# Build and test desktop and CLI packages

The source build requires Git and a full Temurin JDK 17.0.21+9. Creating and
testing native archives also requires Python 3.12 or newer. Set `JAVA_HOME` to
that JDK installation. Maven 3.9.9, build plugins, and OpenJFX 17.0.21 are pinned.
Maven verifies the wrapper distribution checksum and downloads dependencies on
the first build. The finished archive includes Java, JavaFX, and the native
JavaFX libraries; users do not need Python, Maven, or a separate Java installation.
The GUI requires a desktop session and the operating system's windowing libraries
(GTK 3 on Linux).

Check out the reviewed release commit and use a clean working tree when preparing
a release. Task branches target `development`; releases reach `main` through a
separate reviewed pull request. From a fresh clone:

```sh
git clone https://github.com/aydsman/comp3110-line-tracker.git
cd comp3110-line-tracker
git switch --detach RELEASE_COMMIT
```

Replace `RELEASE_COMMIT` with the exact reviewed commit. Until the packaging
changes are merged, check out their task branch explicitly instead of assuming
they are on `main` or `development`.

macOS/Linux:

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
python3 scripts/package.py
python3 scripts/test_package.py target/dist/ARCHIVE_NAME.tar.gz --report target/package-smoke.json
```

Windows PowerShell:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
python scripts/package.py
python scripts/test_package.py target/dist/ARCHIVE_NAME.zip --report target/package-smoke.json
```

On Linux without a desktop display, install GTK 3 and Xvfb, then prefix the test
command with `xvfb-run -a`. CI does this explicitly. A missing display is a failed
GUI acceptance check, not a silently skipped test.

`package.py` prints the resulting archive path. Substitute that exact path in
the test command. It checks the successful packaged-JAR test report and rejects
Java sources or a POM newer than the JAR. These freshness checks support the
required clean build; they do not replace it. Packaging never silently rebuilds
the application, writes only under `target/dist`, and refuses to overwrite an
existing archive. The optional test report also uses exclusive file creation.
Preserve or remove only the named previous artifact before repeating a build.

Each archive contains:

- `line-tracker` / `line-tracker.cmd`: CLI launcher, preserving arguments and exit codes.
- `line-tracker-gui` / `line-tracker-gui.cmd`: JavaFX desktop launcher.
- `app/line-tracker.jar` and the three platform-specific JavaFX module JARs in `app/lib`.
- A Java 17 runtime built with `jlink`, including the JDK modules required by the
  application and JavaFX descriptors. JavaFX stays on the module path and the
  application stays on the class path; the CLI remains runnable with `-jar`.
- Synthetic identical, insert/delete, split-wrap, and empty-file examples.
- Runtime legal notices and unmodified OpenJFX license/third-party notices under
  `legal/openjfx`, with official source URLs, pinned source revision, and hashes.
- `build-info.json` with source commit, dirty state, JDK version/vendor,
  platform/architecture, actual runtime module inventory, JavaFX version and
  classifier, application/dependency hashes, and native-library inventory.

Maven copies runtime dependencies into `target/dependency` during `package`.
The packaging script requires exactly OpenJFX base/graphics/controls 17.0.21 for
the selected host classifier, checks native object headers for the runtime CPU,
and resolves the application's module dependencies with `jdeps`. It derives JDK
modules from those results and the JavaFX descriptors, then verifies the generated
runtime's module resolution. Added runtime libraries or a JavaFX version change
require an explicit packaging and license review. Empty unclassified Maven JARs
are not included in the delivered module path.

macOS/Linux archives use tar.gz to retain executable permissions; Windows uses
ZIP. Build and test each archive on its target OS using a JDK for its target CPU.
Supported classifiers are Windows x86_64, Linux x86_64/ARM64, and macOS x86_64/ARM64;
CI tests the architectures supplied by its three hosted runners. Do not rename
an ARM64 archive to imply x86_64 support. macOS packages are unsigned. A dirty
package is development evidence, not a clean release, and build-time metadata
means archives are not claimed byte-identical across builds.

The smoke test extracts the completed archive into a path containing spaces,
invokes both launchers from a different directory, removes `JAVA_HOME`, and gives
the children an empty `PATH`. CLI checks cover baseline, advanced edits and splits,
independently parsed JSON, UTF-8 exports, errors, and exit codes. The GUI launcher
runs `--smoke-test REPORT_PATH`: it opens a real JavaFX stage, drives actual controls,
and verifies asynchronous comparisons, mapping highlights, baseline/advanced/split
results, text/JSON exports, invalidation, and error/empty states. It must write a
successful machine-readable report and exit within 60 seconds. The test supplies
a fresh `javafx.cachedir` so a previous native-library cache cannot mask incomplete
packaging. Metadata, application and dependency hashes, module inventory, and legal
notices are verified before the application checks.

Automated GUI interaction establishes behavior in the tested desktop environment.
Visual layout/usability inspection and the final QA matrix still belong to release
acceptance. Record the exact final commit, target platform, and package checksum;
a successful feature-branch package does not establish final release sign-off.
