Feature: Analyze Maven dependencies
  As a Java developer
  I want DGViz to analyze a multi-module Maven project
  So that I can see version conflicts early

  Scenario: Detect conflicting versions in a Maven project
    Given a multi-module Maven project with conflicting guava versions
    When the project is analyzed
    Then the analysis should report a version conflict for "com.google.guava:guava"
    And the analysis report should contain at least 1 conflict
