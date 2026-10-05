package io.github.dgviz.parser;

/**
 * Thrown when no build-system parser matches the project.
 */
public class UnsupportedBuildSystemException extends RuntimeException {

    public UnsupportedBuildSystemException(String message) {
        super(message);
    }
}
