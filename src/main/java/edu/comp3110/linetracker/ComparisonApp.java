package edu.comp3110.linetracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Aadi's two-file desktop view, integrated with shared advanced matching and exports. */
public final class ComparisonApp extends Application {
  private final ComparisonService service = new ComparisonService();
  private FilePane oldFile;
  private FilePane newFile;
  private ListView<ComparisonRow> mappings;
  private ComboBox<ComparisonService.Mode> mode;
  private ComboBox<ComparisonService.ResourceProfile> profile;
  private ComboBox<ComparisonService.ExportFormat> exportFormat;
  private Label status;
  private Button compare;
  private Button export;
  private ProgressIndicator progress;
  private ComparisonSession session;
  private Task<?> activeTask;
  private boolean busy;

  @Override
  public void start(Stage stage) {
    oldFile = new FilePane(stage, "Old");
    newFile = new FilePane(stage, "New");
    var files = new SplitPane(oldFile.view, newFile.view);
    files.setDividerPositions(0.5);

    mode = new ComboBox<>();
    mode.setId("comparison-mode");
    mode.setAccessibleText("Matching mode");
    mode.getItems().setAll(ComparisonService.Mode.values());
    mode.setValue(ComparisonService.Mode.BASELINE);
    var modeLabel = new Label("Mode");
    modeLabel.setLabelFor(mode);
    profile = new ComboBox<>();
    profile.setId("resource-profile");
    profile.setAccessibleText("Comparison resource profile");
    profile.getItems().setAll(ComparisonService.ResourceProfile.values());
    profile.setValue(ComparisonService.ResourceProfile.DEFAULT);
    profile.setTooltip(
        new Tooltip("The benchmark profile allows larger comparisons and uses more memory."));
    var profileLabel = new Label("Memory profile");
    profileLabel.setLabelFor(profile);
    var settings = new HBox(10, modeLabel, mode, profileLabel, profile);
    settings.setAlignment(Pos.CENTER_LEFT);
    settings.setPadding(new Insets(0, 0, 10, 0));

    compare = new Button("Compare");
    compare.setId("compare-files");
    compare.setMinWidth(Region.USE_PREF_SIZE);
    compare.setDefaultButton(true);
    compare.setOnAction(event -> compareFiles());
    status = new Label("Choose the old and new UTF-8 files.");
    status.setId("comparison-status");
    status.setWrapText(true);
    status.setMinWidth(0);
    status.setMaxWidth(Double.MAX_VALUE);
    progress = new ProgressIndicator();
    progress.setAccessibleText("Working");
    progress.setPrefSize(20, 20);
    progress.setMaxSize(20, 20);
    progress.setVisible(false);
    progress.managedProperty().bind(progress.visibleProperty());
    var actions = new HBox(12, compare, progress, status);
    actions.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(status, Priority.ALWAYS);

    mappings = new ListView<>();
    mappings.setId("mapping-rows");
    mappings.setAccessibleText("Mappings and unmatched lines, old to new");
    mappings.setPlaceholder(new Label("Compare two files to see mappings."));
    mappings.setPrefHeight(150);
    mappings.setCellFactory(
        list ->
            new ListCell<>() {
              @Override
              protected void updateItem(ComparisonRow row, boolean empty) {
                super.updateItem(row, empty);
                setText(empty || row == null ? null : row.label());
              }
            });
    mappings
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              oldFile.highlight(
                  selected == null ? List.of() : selected.oldLines(),
                  selected == null ? null : selected.kind());
              newFile.highlight(
                  selected == null ? List.of() : selected.newLines(),
                  selected == null ? null : selected.kind());
            });
    var mappingLabel = new Label("Mappings and unmatched lines (old → new)");
    mappingLabel.setLabelFor(mappings);
    exportFormat = new ComboBox<>();
    exportFormat.setId("export-format");
    exportFormat.setAccessibleText("Export format");
    exportFormat.setPromptText("Export format");
    exportFormat.getItems().setAll(ComparisonService.ExportFormat.values());
    export = new Button("Export…");
    export.setId("export-mappings");
    export.setOnAction(event -> chooseExport(stage));
    var exportActions = new HBox(8, exportFormat, export);
    exportActions.setAlignment(Pos.CENTER_LEFT);
    var results = new VBox(6, mappingLabel, mappings, exportActions);
    results.setPadding(new Insets(10, 0, 0, 0));

    mode.valueProperty().addListener((observable, previous, selected) -> inputsChanged());
    profile.valueProperty().addListener((observable, previous, selected) -> inputsChanged());
    exportFormat.valueProperty().addListener((observable, previous, selected) -> updateControls());
    updateControls();

    var root = new BorderPane(files, settings, null, new VBox(10, actions, results), null);
    root.setPadding(new Insets(12));
    BorderPane.setMargin(files, new Insets(0, 0, 12, 0));
    stage.setTitle("Line Tracker");
    stage.setMinWidth(660);
    stage.setMinHeight(520);
    stage.setScene(new Scene(root, 1020, 720));
    stage.show();

    List<String> arguments = getParameters().getRaw();
    if (!arguments.isEmpty() && arguments.get(0).equals("--smoke-test")) {
      if (arguments.size() != 2) {
        System.err.println("Usage: line-tracker-gui --smoke-test OUTPUT_JSON");
        stage.close();
        System.exit(2);
      }
      GuiSmokeRunner.start(this, stage, Path.of(arguments.get(1)));
    } else if (!arguments.isEmpty()) {
      try {
        preselect(arguments);
      } catch (IllegalArgumentException e) {
        status.setText("Launch options: " + e.getMessage());
      }
    }
  }

  private void preselect(List<String> arguments) {
    Path oldPath = null;
    Path newPath = null;
    for (int i = 0; i < arguments.size(); i++) {
      String argument = arguments.get(i);
      switch (argument) {
        case "--old", "--new" -> {
          if (++i == arguments.size())
            throw new IllegalArgumentException(argument + " needs a path.");
          if (argument.equals("--old")) oldPath = Path.of(arguments.get(i));
          else newPath = Path.of(arguments.get(i));
        }
        case "--advanced" -> mode.setValue(ComparisonService.Mode.ADVANCED);
        case "--benchmark" -> profile.setValue(ComparisonService.ResourceProfile.BENCHMARK);
        default ->
            throw new IllegalArgumentException(
                "Use --old PATH --new PATH [--advanced] [--benchmark].");
      }
    }
    selectInputs(oldPath, newPath);
  }

  @Override
  public void stop() {
    if (activeTask != null) activeTask.cancel();
  }

  private void inputsChanged() {
    clearResults();
    status.setText(
        oldFile.path != null && newFile.path != null
            ? "Ready to compare."
            : "Choose the old and new UTF-8 files.");
  }

  private void compareFiles() {
    if (busy || oldFile.path == null || newFile.path == null) return;
    Path oldPath = oldFile.path;
    Path newPath = newFile.path;
    ComparisonService.Mode selectedMode = mode.getValue();
    MatchConfig config = profile.getValue().config();
    clearResults();
    status.setText("Comparing files…");
    runTask(
        new Task<ComparisonSession>() {
          @Override
          protected ComparisonSession call() throws IOException {
            return service.compare(oldPath, newPath, selectedMode, config);
          }
        },
        display -> {
          session = display;
          oldFile.show(display.oldSource());
          newFile.show(display.newSource());
          MappingResult result = display.result();
          mappings.getItems().setAll(display.rows());
          boolean empty =
              display.oldSource().lines().isEmpty() && display.newSource().lines().isEmpty();
          mappings.setPlaceholder(
              new Label(empty ? "Both files are empty." : "No matching lines."));
          status.setText(
              empty
                  ? "Both files are empty. Nothing to map."
                  : (result.matches().isEmpty() ? "No matching lines. " : "")
                      + "%d matched groups · %d deleted (unmatched old) · %d added (unmatched new)"
                          .formatted(
                              result.matches().size(),
                              result.deleted().size(),
                              result.added().size()));
          exportFormat.setValue(ComparisonService.ExportFormat.TEXT);
        },
        "Comparison failed. ");
  }

  private void chooseExport(Stage stage) {
    if (busy || session == null || exportFormat.getValue() == null) return;
    ComparisonService.ExportFormat format = exportFormat.getValue();
    var picker = new FileChooser();
    picker.setTitle("Export mappings to a new file");
    picker.setInitialFileName("mappings." + format.extension());
    picker
        .getExtensionFilters()
        .add(new FileChooser.ExtensionFilter(format + " files", "*." + format.extension()));
    var selected = picker.showSaveDialog(stage);
    if (selected != null) exportTo(selected.toPath());
  }

  /** Same path used by the save dialog and the real-window smoke check. */
  void exportTo(Path output) {
    if (busy || session == null || exportFormat.getValue() == null) return;
    ComparisonSession captured = session;
    ComparisonService.ExportFormat format = exportFormat.getValue();
    exportFormat.setValue(null);
    status.setText("Exporting mappings…");
    runTask(
        new Task<Path>() {
          @Override
          protected Path call() throws IOException {
            service.export(captured, format, output);
            return output;
          }
        },
        path -> {
          exportFormat.setValue(format);
          status.setText("Export saved: " + path);
        },
        "Export failed. ");
  }

  private <T> void runTask(Task<T> task, Consumer<T> success, String failurePrefix) {
    activeTask = task;
    setBusy(true);
    task.setOnSucceeded(
        event -> {
          success.accept(task.getValue());
          activeTask = null;
          setBusy(false);
        });
    task.setOnFailed(
        event -> {
          Throwable failure = task.getException();
          status.setText(
              failurePrefix
                  + (failure.getMessage() == null
                      ? failure.getClass().getSimpleName()
                      : failure.getMessage()));
          activeTask = null;
          setBusy(false);
        });
    task.setOnCancelled(
        event -> {
          activeTask = null;
          status.setText("Operation cancelled.");
          setBusy(false);
        });
    Thread worker = new Thread(task, "line-tracker-desktop-worker");
    worker.setDaemon(true);
    worker.start();
  }

  private void clearResults() {
    session = null;
    mappings.getSelectionModel().clearSelection();
    mappings.getItems().clear();
    mappings.setPlaceholder(new Label("Compare two files to see mappings."));
    oldFile.clear();
    newFile.clear();
    exportFormat.setValue(null);
    updateControls();
  }

  private void setBusy(boolean active) {
    busy = active;
    if (active) mappings.getSelectionModel().clearSelection();
    progress.setVisible(active);
    updateControls();
  }

  private void updateControls() {
    compare.setDisable(busy || oldFile.path == null || newFile.path == null);
    oldFile.choose.setDisable(busy);
    newFile.choose.setDisable(busy);
    mode.setDisable(busy);
    profile.setDisable(busy);
    mappings.setDisable(busy);
    exportFormat.setDisable(busy || session == null);
    export.setDisable(busy || session == null || exportFormat.getValue() == null);
  }

  /** Input selection seam shared by the native chooser and the real-window smoke check. */
  void selectInputs(Path oldPath, Path newPath) {
    if (busy) throw new IllegalStateException("Wait for the current operation to finish.");
    oldFile.select(oldPath);
    newFile.select(newPath);
    inputsChanged();
  }

  boolean isBusy() {
    return busy;
  }

  ComparisonSession displayedSession() {
    return session;
  }

  Set<Integer> highlightedOldLines() {
    return oldFile.highlighted;
  }

  Set<Integer> highlightedNewLines() {
    return newFile.highlighted;
  }

  private final class FilePane {
    private final VBox view;
    private final Button choose;
    private final Label name;
    private final ListView<SourceLine> source;
    private Path path;
    private Set<Integer> highlighted = Set.of();
    private ComparisonRow.Kind highlightedKind;
    private int numberWidth = 1;

    private FilePane(Stage stage, String side) {
      source = new ListView<>();
      source.setId(side.toLowerCase(Locale.ROOT) + "-source");
      source.setAccessibleText(side + " file source with line numbers");
      source.setPlaceholder(new Label(""));
      source.setCellFactory(
          list ->
              new ListCell<>() {
                @Override
                protected void updateItem(SourceLine line, boolean empty) {
                  super.updateItem(line, empty);
                  setText(
                      empty || line == null
                          ? null
                          : String.format(
                              Locale.ROOT,
                              "%" + numberWidth + "d  %s",
                              line.number(),
                              line.original()));
                  boolean marked = !empty && line != null && highlighted.contains(line.number());
                  setStyle(
                      "-fx-font-family: monospace;"
                          + (marked
                              ? " -fx-background-color: "
                                  + highlightColor()
                                  + "; -fx-text-fill: #14293d;"
                              : ""));
                }
              });
      choose = new Button("Choose " + side.toLowerCase(Locale.ROOT) + " file…");
      choose.setId("choose-" + side.toLowerCase(Locale.ROOT) + "-file");
      name = new Label("No file selected");
      name.setMinWidth(0);
      var header = new HBox(8, choose, name);
      header.setAlignment(Pos.CENTER_LEFT);
      var picker = new FileChooser();
      picker.setTitle(choose.getText());
      choose.setOnAction(
          event -> {
            var selected = picker.showOpenDialog(stage);
            if (selected != null) {
              select(selected.toPath());
              inputsChanged();
            }
          });
      view = new VBox(6, header, source);
      view.setPadding(new Insets(8));
      VBox.setVgrow(source, Priority.ALWAYS);
    }

    private void select(Path selected) {
      path = selected == null ? null : selected.toAbsolutePath().normalize();
      name.setText(
          path == null
              ? "No file selected"
              : path.getFileName() == null ? path.toString() : path.getFileName().toString());
      name.setTooltip(path == null ? null : new Tooltip(path.toString()));
    }

    private void show(SourceFile file) {
      numberWidth = Integer.toString(file.lines().size()).length();
      source.getItems().setAll(file.lines());
      source.setPlaceholder(new Label("Empty file."));
      if (!file.lines().isEmpty()) source.scrollTo(0);
    }

    private void clear() {
      highlighted = Set.of();
      highlightedKind = null;
      source.getSelectionModel().clearSelection();
      source.getItems().clear();
      source.setPlaceholder(new Label(""));
    }

    private void highlight(List<Integer> lines, ComparisonRow.Kind kind) {
      highlighted = Set.copyOf(lines);
      highlightedKind = kind;
      source.refresh();
      if (!lines.isEmpty()) source.scrollTo(lines.get(0) - 1);
    }

    private String highlightColor() {
      if (highlightedKind == null) return "#b9ddff";
      return switch (highlightedKind) {
        case UNCHANGED -> "#b9ddff";
        case MODIFIED -> "#fff0ae";
        case MOVED -> "#e3d3ff";
        case SPLIT -> "#b5e9ed";
        case DELETED -> "#ffd0d0";
        case ADDED -> "#c9edcb";
      };
    }
  }
}
