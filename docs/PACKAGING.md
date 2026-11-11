# Build and test native CLI packages

The source build requires Git and a full Temurin JDK 17.0.21+9.
Creating and testing native archives also requires Python 3.12 or newer.
Set `JAVA_HOME` to the JDK installation directory. Maven 3.9.9 is downloaded by
the checked-in wrapper and its distribution checksum is pinned. Initial Maven
and test dependencies require network access. The finished package includes
its runtime and does not require Python, Maven or a separate Java installation.

Check out the reviewed release commit and use a clean working tree when
preparing a release. Task branches target `development`; an unreviewed package
does not change that review requirement. From a fresh clone:

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
python3 scripts/test_package.py target/dist/ARCHIVE_NAME.tar.gz
```

Windows PowerShell:

```powershell
.\mvnw.cmd --batch-mode --no-transfer-progress clean verify
python scripts/package.py
python scripts/test_package.py target/dist/ARCHIVE_NAME.zip
```

`package.py` prints the resulting archive path. Substitute that exact path in
the test command. It checks for a successful packaged-JAR test report and
rejects Java sources or a POM newer than the JAR. These freshness checks support
the required clean build; they do not replace it. The script never silently
rebuilds the application. It exclusively creates artifacts under `target/dist`
and refuses to overwrite an existing archive. Preserve or remove only the
named previous archive before repeating an identical package build.

Each archive contains the application JAR, a native `java.base` runtime made
with JDK 17 `jlink`, launchers, synthetic examples, usage instructions and
`build-info.json`. Runtime legal notices remain included. Build metadata records
the source commit, whether the tree was dirty, JDK version/vendor, platform,
runtime architecture and SHA-256 of the JAR. A dirty package is development
evidence, not a clean release. The package script rejects dependencies beyond
`java.base`; review runtime modules if production dependencies change.

macOS/Linux use tar.gz to retain executable permissions. Windows uses ZIP.
Build packages on their target operating system with a JDK for their target
architecture. Do not rename a macOS ARM64 archive to imply Windows/Linux or
x86_64 support. macOS packages are unsigned. JavaFX is not present, so this
distribution supplies the CLI only.

The smoke test extracts the finished archive into a temporary path containing
spaces, invokes the launcher from another directory, unsets `JAVA_HOME`, and
sets `PATH` to an empty directory. It checks baseline and advanced behavior,
split mappings, JSON through Python's independent JSON parser, export files,
errors, exit codes, JAR checksum and runtime legal files. Test each target
archive on its matching runner. Do not claim a platform passed based solely on
another platform's package or Java unit tests.

Publishing packages, resolving the instructor's bug-tracing requirement,
evaluating the existing benchmark and integrating the future GUI remain
separate review and evidence requirements.
