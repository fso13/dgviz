package io.github.dgviz.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Runtime configuration loaded from YAML/JSON or environment variables.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DgvizConfig {

    private String gitlabUrl = "https://gitlab.com";
    private String gitlabToken = "";
    private String projectPath = ".";
    private String outputDir = "reports";
    private boolean failOnConflict = false;
    private double failOnCvss = 0.0;
    private int serverPort = 8080;

    public String getGitlabUrl() {
        return gitlabUrl;
    }

    public void setGitlabUrl(String gitlabUrl) {
        this.gitlabUrl = gitlabUrl;
    }

    public String getGitlabToken() {
        return gitlabToken;
    }

    public void setGitlabToken(String gitlabToken) {
        this.gitlabToken = gitlabToken;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public boolean isFailOnConflict() {
        return failOnConflict;
    }

    public void setFailOnConflict(boolean failOnConflict) {
        this.failOnConflict = failOnConflict;
    }

    public double getFailOnCvss() {
        return failOnCvss;
    }

    public void setFailOnCvss(double failOnCvss) {
        this.failOnCvss = failOnCvss;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
    }

    public static DgvizConfig load(Path optionalFile) {
        DgvizConfig config = new DgvizConfig();
        if (optionalFile != null && Files.isRegularFile(optionalFile)) {
            config = readFile(optionalFile);
        } else {
            Path defaultYaml = Path.of("dgviz.yml");
            Path defaultJson = Path.of("dgviz.json");
            if (Files.isRegularFile(defaultYaml)) {
                config = readFile(defaultYaml);
            } else if (Files.isRegularFile(defaultJson)) {
                config = readFile(defaultJson);
            }
        }
        applyEnv(config);
        return config;
    }

    private static DgvizConfig readFile(Path file) {
        try {
            ObjectMapper mapper = file.getFileName().toString().endsWith(".json")
                    ? tools.jackson.databind.json.JsonMapper.builder().build()
                    : tools.jackson.dataformat.yaml.YAMLMapper.builder().build();
            return mapper.readValue(file.toFile(), DgvizConfig.class);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to read config " + file, e);
        }
    }

    private static void applyEnv(DgvizConfig config) {
        env("GITLAB_TOKEN").or(() -> env("CI_JOB_TOKEN")).ifPresent(config::setGitlabToken);
        env("GITLAB_URL").ifPresent(config::setGitlabUrl);
        env("DGVIZ_PROJECT_PATH").ifPresent(config::setProjectPath);
        env("DGVIZ_OUTPUT_DIR").ifPresent(config::setOutputDir);
        env("DGVIZ_FAIL_ON_CONFLICT").ifPresent(v -> config.setFailOnConflict(Boolean.parseBoolean(v)));
        env("DGVIZ_FAIL_ON_CVSS").ifPresent(v -> config.setFailOnCvss(Double.parseDouble(v)));
        env("DGVIZ_SERVER_PORT").ifPresent(v -> config.setServerPort(Integer.parseInt(v)));
    }

    private static Optional<String> env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
