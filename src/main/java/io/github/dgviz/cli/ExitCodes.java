package io.github.dgviz.cli;

/**
 * Process exit codes as defined in FR-5.3.
 */
public final class ExitCodes {

    public static final int SUCCESS = 0;
    public static final int PROBLEMS_FOUND = 1;
    public static final int EXECUTION_ERROR = 2;

    private ExitCodes() {
    }
}
