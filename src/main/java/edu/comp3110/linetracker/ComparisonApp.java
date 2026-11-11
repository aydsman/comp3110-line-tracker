package edu.comp3110.linetracker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
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
  private TextArea mappings;
  private Label status;
  private Button compare;

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
    var actions = new HBox(12, compare, status);
    actions.setAlignment(Pos.CENTER_LEFT);

    mappings = readOnlyText("Baseline mappings");
    mappings.setPromptText("Matching old and new line numbers will appear here.");
    mappings.setPrefRowCount(5);
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
            return new Comparison(
                numberedText(oldSource),
                numberedText(newSource),
                TextMappingFormatter.format(result),
                result);
          }
        };
    comparison.setOnSucceeded(
        event -> {
          Comparison display = comparison.getValue();
          oldFile.source.setText(display.oldText());
          newFile.source.setText(display.newText());
          mappings.setText(display.pairs().isEmpty() ? "No matching lines." : display.pairs());
          MappingResult result = display.result();
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
    oldFile.source.clear();
    newFile.source.clear();
    mappings.clear();
  }

  private void setComparing(boolean active) {
    compare.setDisable(active);
    oldFile.choose.setDisable(active);
    newFile.choose.setDisable(active);
  }

  private static SourceFile read(Path path, String side) throws IOException {
    try {
      return SourceReader.read(path);
    } catch (IOException e) {
      throw new IOException("Cannot read " + side + " file as UTF-8. " + e.getMessage(), e);
    }
  }

  private static String numberedText(SourceFile file) {
    var text = new StringBuilder();
    int width = Integer.toString(file.lines().size()).length();
    for (SourceLine line : file.lines()) {
      String number = Integer.toString(line.number());
      text.append(" ".repeat(width - number.length())).append(number).append("  ");
      text.append(line.original());
      if (line.number() < file.lines().size()) {
        text.append('\n');
      }
    }
    return text.toString();
  }

  private static TextArea readOnlyText(String accessibleName) {
    var area = new TextArea();
    area.setEditable(false);
    area.setAccessibleText(accessibleName);
    area.setStyle("-fx-font-family: monospace;");
    return area;
  }

  private record Comparison(String oldText, String newText, String pairs, MappingResult result) {}

  private final class FilePane {
    private final VBox view;
    private final Button choose;
    private final TextArea source;
    private Path path;

    private FilePane(Stage stage, String side) {
      source = readOnlyText(side + " file source with line numbers");
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
  }
}
