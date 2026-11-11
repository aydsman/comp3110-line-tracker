package edu.comp3110.linetracker;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/** Desktop view of the shared baseline comparison. */
public final class ComparisonApp extends Application {
  private final MappingPipeline pipeline = new MappingPipeline(new BaselineMatcher());
  private FilePane oldFile;
  private FilePane newFile;
  private ListView<Match> mappings;
  private Label status;
  private Button compare;
  private ProgressIndicator progress;

  @Override
  public void start(Stage stage) {
    oldFile = new FilePane(stage, "Old");
    newFile = new FilePane(stage, "New");
    var files = new SplitPane(oldFile.view, newFile.view);
    files.setDividerPositions(0.5);

    compare = new Button("Compare");
    compare.setDefaultButton(true);
    compare.setDisable(true);
    compare.setOnAction(event -> compareFiles());
    status = new Label("Choose the old and new UTF-8 files.");
    status.setWrapText(true);
    progress = new ProgressIndicator();
    progress.setAccessibleText("Comparing files");
    progress.setPrefSize(20, 20);
    progress.setMaxSize(20, 20);
    progress.setVisible(false);
    progress.managedProperty().bind(progress.visibleProperty());
    var actions = new HBox(12, compare, progress, status);
    actions.setAlignment(Pos.CENTER_LEFT);

    mappings = new ListView<>();
    mappings.setAccessibleText("Baseline mappings");
    mappings.setPlaceholder(new Label("Compare two files to see mappings."));
    mappings.setPrefHeight(130);
    mappings.setCellFactory(
        list ->
            new ListCell<>() {
              @Override
              protected void updateItem(Match match, boolean empty) {
                super.updateItem(match, empty);
                setText(
                    empty
                        ? null
                        : lineNumbers(match.oldLines()) + " → " + lineNumbers(match.newLines()));
              }
            });
    mappings
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (observable, previous, selected) -> {
              oldFile.highlight(selected == null ? List.of() : selected.oldLines());
              newFile.highlight(selected == null ? List.of() : selected.newLines());
            });
    var mappingLabel = new Label("Baseline mappings (old–new)");
    mappingLabel.setLabelFor(mappings);
    var results = new VBox(6, mappingLabel, mappings);
    results.setPadding(new Insets(10, 0, 0, 0));

    var root = new BorderPane(files, null, null, new VBox(10, actions, results), null);
    root.setPadding(new Insets(12));
    BorderPane.setMargin(files, new Insets(0, 0, 12, 0));
    stage.setTitle("Line Tracker");
    stage.setMinWidth(560);
    stage.setMinHeight(420);
    stage.setScene(new Scene(root, 960, 660));
    stage.show();
  }

  private void fileSelected() {
    clearResults();
    boolean ready = oldFile.path != null && newFile.path != null;
    compare.setDisable(!ready);
    status.setText(ready ? "Ready to compare." : "Choose the other file.");
  }

  private void compareFiles() {
    Path oldPath = oldFile.path;
    Path newPath = newFile.path;
    clearResults();
    setComparing(true);
    status.setText("Comparing files…");

    Task<Comparison> comparison =
        new Task<>() {
          @Override
          protected Comparison call() throws IOException {
            SourceFile oldSource = read(oldPath, "old");
            SourceFile newSource = read(newPath, "new");
            MappingResult result = pipeline.compare(oldSource, newSource, MatchConfig.DEFAULT);
            return new Comparison(oldSource, newSource, result);
          }
        };
    comparison.setOnSucceeded(
        event -> {
          Comparison display = comparison.getValue();
          oldFile.show(display.oldSource());
          newFile.show(display.newSource());
          MappingResult result = display.result();
          mappings.getItems().setAll(result.matches());
          mappings.setPlaceholder(new Label("No matching lines."));
          status.setText(
              "%d matched · %d unmatched old · %d unmatched new"
                  .formatted(
                      result.matches().size(), result.deleted().size(), result.added().size()));
          setComparing(false);
        });
    comparison.setOnFailed(
        event -> {
          status.setText("Comparison failed. " + comparison.getException().getMessage());
          setComparing(false);
        });

    Thread worker = new Thread(comparison, "line-tracker-comparison");
    worker.setDaemon(true);
    worker.start();
  }

  private void clearResults() {
    mappings.getSelectionModel().clearSelection();
    mappings.getItems().clear();
    mappings.setPlaceholder(new Label("Compare two files to see mappings."));
    oldFile.clear();
    newFile.clear();
  }

  private void setComparing(boolean active) {
    compare.setDisable(active);
    oldFile.choose.setDisable(active);
    newFile.choose.setDisable(active);
    progress.setVisible(active);
  }

  private static SourceFile read(Path path, String side) throws IOException {
    try {
      return SourceReader.read(path);
    } catch (NoSuchFileException e) {
      throw new IOException("The " + side + " file is missing. Choose it again.", e);
    } catch (CharacterCodingException e) {
      throw new IOException("The " + side + " file is not valid UTF-8.", e);
    } catch (IOException e) {
      throw new IOException(
          "Cannot read the " + side + " file (" + path.getFileName() + "). " + e.getMessage(), e);
    }
  }

  private static String lineNumbers(List<Integer> lines) {
    return lines.stream().map(String::valueOf).collect(Collectors.joining(", "));
  }

  private record Comparison(SourceFile oldSource, SourceFile newSource, MappingResult result) {}

  private final class FilePane {
    private final VBox view;
    private final Button choose;
    private final ListView<SourceLine> source;
    private Path path;
    private Set<Integer> highlighted = Set.of();
    private int numberWidth = 1;

    private FilePane(Stage stage, String side) {
      source = new ListView<>();
      source.setAccessibleText(side + " file source with line numbers");
      source.setPlaceholder(new Label(""));
      source.setCellFactory(
          list ->
              new ListCell<>() {
                @Override
                protected void updateItem(SourceLine line, boolean empty) {
                  super.updateItem(line, empty);
                  setText(
                      empty
                          ? null
                          : String.format(
                              Locale.ROOT,
                              "%" + numberWidth + "d  %s",
                              line.number(),
                              line.original()));
                  boolean marked = !empty && highlighted.contains(line.number());
                  setStyle(
                      marked
                          ? "-fx-font-family: monospace; -fx-background-color: #b9ddff; -fx-text-fill: #14293d;"
                          : "-fx-font-family: monospace;");
                }
              });
      choose = new Button("Choose " + side.toLowerCase(Locale.ROOT) + " file…");
      var name = new Label("No file selected");
      name.setMinWidth(0);
      var header = new HBox(8, choose, name);
      header.setAlignment(Pos.CENTER_LEFT);
      var picker = new FileChooser();
      picker.setTitle(choose.getText());
      choose.setOnAction(
          event -> {
            var selected = picker.showOpenDialog(stage);
            if (selected != null) {
              path = selected.toPath();
              name.setText(selected.getName());
              name.setTooltip(new Tooltip(selected.getAbsolutePath()));
              fileSelected();
            }
          });
      view = new VBox(6, header, source);
      view.setPadding(new Insets(8));
      VBox.setVgrow(source, Priority.ALWAYS);
    }

    private void show(SourceFile file) {
      numberWidth = Integer.toString(file.lines().size()).length();
      source.getItems().setAll(file.lines());
      source.setPlaceholder(new Label("Empty file."));
      if (!file.lines().isEmpty()) {
        source.scrollTo(0);
      }
    }

    private void clear() {
      highlighted = Set.of();
      source.getSelectionModel().clearSelection();
      source.getItems().clear();
      source.setPlaceholder(new Label(""));
    }

    private void highlight(List<Integer> lines) {
      highlighted = Set.copyOf(lines);
      source.refresh();
      if (!lines.isEmpty()) {
        source.scrollTo(lines.get(0) - 1);
      }
    }
  }
}
