package edu.comp3110.linetracker;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  public static int run(String[] args, PrintStream out, PrintStream err) {
    boolean advanced = args.length == 4 && args[3].equals("--advanced");
    if ((args.length != 3 && !advanced) || !args[0].equals("map")) {
      err.println("Usage: java -jar target/line-tracker.jar map OLD NEW [--advanced]");
      return 2;
    }
    try {
      var result =
          new MappingPipeline(advanced ? new AdvancedMatcher() : new BaselineMatcher())
              .compare(Path.of(args[1]), Path.of(args[2]), MatchConfig.DEFAULT);
      for (Match match : result.matches())
        for (int oldLine : match.oldLines())
          for (int newLine : match.newLines()) out.println(oldLine + "-" + newLine);
      err.println("Unmatched old lines: " + result.deleted());
      err.println("Unmatched new lines: " + result.added());
      return 0;
    } catch (IOException | IllegalArgumentException e) {
      err.println("Comparison failed: " + e.getMessage());
      return 1;
    }
  }
}
