package edu.comp3110.linetracker;

public interface LineMatcher {
  MappingResult match(SourceFile oldFile, SourceFile newFile, MatchConfig config);
}
