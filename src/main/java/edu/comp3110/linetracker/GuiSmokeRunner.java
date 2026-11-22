package edu.comp3110.linetracker;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

/** Optional release diagnostic: exercises a real, displayed JavaFX application and its workers. */
final class GuiSmokeRunner {
  private final ComparisonApp app;
  private final Stage stage;
  private int checks;

  private GuiSmokeRunner(ComparisonApp app, Stage stage) {
    this.app = app;
    this.stage = stage;
  }

  static void start(ComparisonApp app, Stage stage, Path report) {
    Thread runner =
        new Thread(
            () -> {
              var smoke = new GuiSmokeRunner(app, stage);
              boolean success = false;
              String message;
              try {
                smoke.run();
                success = true;
                message = "Real JavaFX workflow and shared CLI export checks passed.";
              } catch (Throwable failure) {
                failure.printStackTrace(System.err);
                message = failure.toString();
              }
              try {
                Files.writeString(
                    report,
                    "{\"success\":"
                        + success
                        + ",\"checks\":"
                        + smoke.checks
                        + ",\"message\":\""
                        + escape(message)
                        + "\"}\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
              } catch (Exception failure) {
                failure.printStackTrace(System.err);
                success = false;
              }
              boolean passed = success;
              Platform.runLater(
                  () -> {
                    stage.close();
                    Platform.exit();
                    System.exit(passed ? 0 : 1);
                  });
            },
            "line-tracker-gui-smoke");
    runner.setDaemon(true);
    runner.start();
  }

  private void run() throws Exception {
    Path folder = Files.createTempDirectory("line-tracker-gui-smoke-");
    Path oldPath =
        Files.writeString(
            folder.resolve("old source.txt"),
            "anchor start\nint count = 1;\nreturn sum(a, b);\nremove this wholly\nanchor end\n");
    Path newPath =
        Files.writeString(
            folder.resolve("new source.txt"),
            "anchor start\nint count = 2;\nreturn sum(a,\nb);\nzzzzqqqqvvvv\nanchor end\n");
    fx(
        () -> {
          check(stage.isShowing(), "A real window must be visible");
          check(button("compare-files").isDisabled(), "Compare must await inputs");
          check(button("export-mappings").isDisabled(), "Export must await a comparison");
          app.selectInputs(oldPath, newPath);
          check(!button("compare-files").isDisabled(), "Selected files enable Compare");
          check(
              combo("resource-profile").getValue() == ComparisonService.ResourceProfile.DEFAULT,
              "The default memory profile must remain 4m");
          return null;
        });
    compare();
    fx(
        () -> {
          check(
              app.displayedSession().result().matches().size() == 2,
              "Baseline keeps exact anchors");
          check(
              rows().getItems().stream().anyMatch(row -> row.kind() == ComparisonRow.Kind.DELETED),
              "Unmatched old lines must be labeled");
          check(
              rows().getItems().stream().anyMatch(row -> row.kind() == ComparisonRow.Kind.ADDED),
              "Unmatched new lines must be labeled");
          return null;
        });
    checkExports(folder, oldPath, newPath, ComparisonService.Mode.BASELINE);
    fx(
        () -> {
          combo("comparison-mode").setValue(ComparisonService.Mode.ADVANCED);
          check(
              app.displayedSession() == null && rows().getItems().isEmpty(),
              "Mode changes clear stale results");
          check(
              button("export-mappings").isDisabled() && combo("export-format").getValue() == null,
              "Mode changes clear and disable exports");
          return null;
        });
    compare();
    fx(
        () -> {
          ComparisonRow split =
              rows().getItems().stream()
                  .filter(row -> row.kind() == ComparisonRow.Kind.SPLIT)
                  .findFirst()
                  .orElseThrow();
          rows().getSelectionModel().select(split);
          check(
              app.highlightedOldLines().equals(Set.of(3)),
              "Selecting a split highlights its old line");
          check(
              app.highlightedNewLines().equals(Set.of(3, 4)),
              "Selecting a split highlights every fragment");
          ComparisonRow added =
              rows().getItems().stream()
                  .filter(row -> row.kind() == ComparisonRow.Kind.ADDED)
                  .findFirst()
                  .orElseThrow();
          rows().getSelectionModel().select(added);
          check(
              app.highlightedOldLines().isEmpty()
                  && app.highlightedNewLines().equals(Set.copyOf(added.newLines())),
              "Added-line selection highlights only the new side");
          ComparisonRow deleted =
              rows().getItems().stream()
                  .filter(row -> row.kind() == ComparisonRow.Kind.DELETED)
                  .findFirst()
                  .orElseThrow();
          rows().getSelectionModel().select(deleted);
          check(
              app.highlightedNewLines().isEmpty()
                  && app.highlightedOldLines().equals(Set.copyOf(deleted.oldLines())),
              "Deleted-line selection highlights only the old side");
          return null;
        });
    checkExports(folder, oldPath, newPath, ComparisonService.Mode.ADVANCED);
    fx(
        () -> {
          combo("resource-profile").setValue(ComparisonService.ResourceProfile.BENCHMARK);
          check(
              app.displayedSession() == null && rows().getItems().isEmpty(),
              "Resource changes clear stale results");
          check(
              app.highlightedOldLines().isEmpty() && app.highlightedNewLines().isEmpty(),
              "Resource changes clear highlights");
          return null;
        });
    compare();
    fx(
        () -> {
          check(
              app.displayedSession().result().config().maxLcsCells() == 8_000_000,
              "Explicit benchmark choice reaches the shared pipeline");
          combo("export-format").setValue(ComparisonService.ExportFormat.TEXT);
          app.exportTo(oldPath);
          return null;
        });
    awaitIdle();
    fx(
        () -> {
          check(status().startsWith("Export failed."), "Input overwrite error must be visible");
          check(app.displayedSession() != null, "Failed export preserves the displayed comparison");
          return null;
        });
    check(
        Files.readString(oldPath).startsWith("anchor start\n"),
        "Failed export preserves input bytes");

    Path empty = Files.writeString(folder.resolve("empty.txt"), "");
    fx(
        () -> {
          app.selectInputs(empty, empty);
          check(
              app.displayedSession() == null && rows().getItems().isEmpty(),
              "Input changes clear stale results");
          return null;
        });
    compare();
    fx(
        () -> {
          check(
              rows().getItems().isEmpty() && status().contains("Both files are empty"),
              "Empty files have an explicit state");
          return null;
        });
    Path unmatched = Files.writeString(folder.resolve("unmatched.txt"), "new content\n");
    fx(
        () -> {
          app.selectInputs(empty, unmatched);
          return null;
        });
    compare();
    fx(
        () -> {
          check(
              status().contains("No matching lines") && rows().getItems().size() == 1,
              "No-match results retain labeled added lines");
          return null;
        });
    fx(
        () -> {
          app.selectInputs(folder.resolve("missing.txt"), unmatched);
          return null;
        });
    compare();
    fx(
        () -> {
          check(
              status().contains("old file is missing"), "Missing input must have a readable error");
          check(
              app.displayedSession() == null && button("export-mappings").isDisabled(),
              "A failed comparison cannot export stale results");
          return null;
        });
    Path invalid = Files.write(folder.resolve("invalid.txt"), new byte[] {(byte) 0xc3, 0x28});
    fx(
        () -> {
          app.selectInputs(unmatched, invalid);
          return null;
        });
    compare();
    fx(
        () -> {
          check(
              status().contains("new file is not valid UTF-8"),
              "Invalid UTF-8 must identify the side");
          return null;
        });
    fx(
        () -> {
          app.selectInputs(oldPath.getRoot(), unmatched);
          return null;
        });
    compare();
    fx(
        () -> {
          String actualStatus = status();
          check(
              actualStatus.startsWith("Comparison failed. Cannot read the old file")
                  && actualStatus.contains("directory"),
              "A filesystem root input must produce a readable directory error. Observed: "
                  + actualStatus);
          return null;
        });
  }

  private void checkExports(Path folder, Path oldPath, Path newPath, ComparisonService.Mode mode)
      throws Exception {
    for (ComparisonService.ExportFormat format : ComparisonService.ExportFormat.values()) {
      Path output = folder.resolve(mode.name() + "." + format.extension());
      fx(
          () -> {
            combo("export-format").setValue(format);
            app.exportTo(output);
            check(
                app.isBusy() && button("export-mappings").isDisabled(),
                "Export must run with controls disabled");
            check(
                combo("export-format").getValue() == null, "Active export clears format selection");
            return null;
          });
      awaitIdle();
      var arguments =
          new ArrayList<>(
              List.of(
                  "map",
                  oldPath.toString(),
                  newPath.toString(),
                  "--format",
                  format.extension().equals("txt") ? "text" : "json"));
      if (mode == ComparisonService.Mode.ADVANCED) arguments.add("--advanced");
      var outBytes = new ByteArrayOutputStream();
      var errBytes = new ByteArrayOutputStream();
      try (var out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
          var err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
        check(
            Main.run(arguments.toArray(String[]::new), out, err) == 0,
            "CLI reference export must succeed");
      }
      check(
          Files.readString(output).equals(outBytes.toString(StandardCharsets.UTF_8)),
          mode + " " + format + " GUI export must equal CLI bytes");
      fx(
          () -> {
            combo("export-format").setValue(format);
            app.exportTo(output);
            return null;
          });
      awaitIdle();
      fx(
          () -> {
            check(
                status().contains("already exists"),
                "Existing export files must be refused clearly");
            return null;
          });
      check(
          Files.readString(output).equals(outBytes.toString(StandardCharsets.UTF_8)),
          "Existing export bytes must survive");
    }
  }

  private void compare() throws Exception {
    fx(
        () -> {
          button("compare-files").fire();
          check(app.isBusy(), "Compare starts the background operation");
          check(
              button("compare-files").isDisabled()
                  && button("choose-old-file").isDisabled()
                  && button("choose-new-file").isDisabled(),
              "Inputs stay disabled while busy");
          check(
              combo("comparison-mode").isDisabled() && combo("resource-profile").isDisabled(),
              "Comparison settings stay disabled while busy");
          check(
              button("export-mappings").isDisabled() && rows().isDisabled(),
              "Results and exports stay disabled while busy");
          return null;
        });
    awaitIdle();
  }

  private void awaitIdle() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (fx(app::isBusy)) {
      if (System.nanoTime() > deadline) throw new IllegalStateException("GUI operation timed out");
      Thread.sleep(20);
    }
  }

  private Button button(String id) {
    return (Button) stage.getScene().lookup("#" + id);
  }

  @SuppressWarnings("unchecked")
  private ComboBox<Object> combo(String id) {
    return (ComboBox<Object>) stage.getScene().lookup("#" + id);
  }

  @SuppressWarnings("unchecked")
  private ListView<ComparisonRow> rows() {
    return (ListView<ComparisonRow>) stage.getScene().lookup("#mapping-rows");
  }

  private String status() {
    return ((Label) stage.getScene().lookup("#comparison-status")).getText();
  }

  private void check(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
    checks++;
  }

  private static <T> T fx(Callable<T> action) throws Exception {
    var future = new CompletableFuture<T>();
    Platform.runLater(
        () -> {
          try {
            future.complete(action.call());
          } catch (Throwable failure) {
            future.completeExceptionally(failure);
          }
        });
    return future.get(10, TimeUnit.SECONDS);
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
