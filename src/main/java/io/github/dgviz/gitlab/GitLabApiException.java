package io.github.dgviz.gitlab;

/**
 * Non-2xx response from GitLab API.
 */
public class GitLabApiException extends RuntimeException {

    private final int statusCode;

    public GitLabApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
