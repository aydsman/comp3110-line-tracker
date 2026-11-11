package edu.comp3110.linetracker;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public final class Main {
  private static final String USAGE =
      """
      Usage: line-tracker map OLD NEW [options]
        --advanced                 Recover edited, moved and split lines
        --format text|json         Output format (default: text)
        --output FILE              Create a new UTF-8 export file instead of stdout
        --max-lcs-cells N          LCS memory guard (default: 4000000)
        --candidate-limit N        Advanced candidate count (default: 15)
        --context-radius N         Advanced neighboring-line radius (default: 4)
        --minimum-score N          Advanced score threshold in (0,1] (default: 0.65)
      Use --help for this help. Options follow OLD NEW; existing output files are refused.
      """;

  private Main() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  public static int run(String[] args, PrintStream out, PrintStream err) {
    if ((args.length == 1 && (args[0].equals("--help") || args[0].equals("help")))
        || (args.length == 2 && args[0].equals("map") && args[1].equals("--help"))) {
      out.print(USAGE);
      return 0;
    }
    CliOptions options;
    try {
      options = CliOptions.parse(args);
    } catch (IllegalArgumentException e) {
      err.println("Invalid arguments: " + e.getMessage());
      err.print(USAGE);
      return 2;
    }
    try {
      var result =
          new MappingPipeline(options.advanced() ? new AdvancedMatcher() : new BaselineMatcher())
              .compare(options.oldPath(), options.newPath(), options.config());
      String export =
          options.json()
              ? JsonMappingFormatter.format(result)
              : TextMappingFormatter.format(result);
      if (options.output() == null) {
        out.print(export);
      } else {
        // CREATE_NEW also refuses existing symlinks/hardlinks, protecting both input files.
        try {
          Files.writeString(
              options.output(),
              export,
              StandardCharsets.UTF_8,
              StandardOpenOption.CREATE_NEW,
              StandardOpenOption.WRITE);
        } catch (IOException e) {
          throw new IOException(
              "Cannot write export " + options.output() + ": " + e.getMessage(), e);
        }
      }
      err.println("Unmatched old lines: " + result.deleted());
      err.println("Unmatched new lines: " + result.added());
      return 0;
    } catch (IOException | IllegalArgumentException e) {
      err.println("Comparison failed: " + e.getMessage());
      return 1;
    }
  }
}
