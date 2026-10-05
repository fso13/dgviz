package io.github.dgviz.model;

/**
 * Dependency scope / configuration across Maven and Gradle.
 */
public enum DependencyScope {
    COMPILE,
    RUNTIME,
    PROVIDED,
    TEST,
    SYSTEM,
    IMPORT,
    IMPLEMENTATION,
    API,
    COMPILE_ONLY,
    RUNTIME_ONLY,
    TEST_IMPLEMENTATION,
    UNKNOWN;

    public static DependencyScope fromMaven(String scope) {
        if (scope == null || scope.isBlank()) {
            return COMPILE;
        }
        return switch (scope.toLowerCase()) {
            case "compile" -> COMPILE;
            case "runtime" -> RUNTIME;
            case "provided" -> PROVIDED;
            case "test" -> TEST;
            case "system" -> SYSTEM;
            case "import" -> IMPORT;
            default -> UNKNOWN;
        };
    }

    public static DependencyScope fromGradle(String configuration) {
        if (configuration == null || configuration.isBlank()) {
            return IMPLEMENTATION;
        }
        return switch (configuration) {
            case "implementation" -> IMPLEMENTATION;
            case "api" -> API;
            case "compileOnly" -> COMPILE_ONLY;
            case "runtimeOnly" -> RUNTIME_ONLY;
            case "testImplementation" -> TEST_IMPLEMENTATION;
            case "compile" -> COMPILE;
            case "runtime" -> RUNTIME;
            case "testCompile", "testRuntime" -> TEST;
            default -> UNKNOWN;
        };
    }
}
