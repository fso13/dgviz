package io.github.dgviz;

import io.github.dgviz.cli.AnalyzeCommand;
import io.github.dgviz.cli.CiCheckCommand;
import io.github.dgviz.cli.ReportCommand;
import io.github.dgviz.cli.VisualizeCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "dgviz",
        mixinStandardHelpOptions = true,
        version = "dgviz 0.1.0",
        description = "Dependency Graph Visualizer with GitLab Integration",
        subcommands = {
                AnalyzeCommand.class,
                VisualizeCommand.class,
                ReportCommand.class,
                CiCheckCommand.class
        }
)
public class DgvizApplication implements Runnable {

    public static void main(String[] args) {
        int code = new CommandLine(new DgvizApplication()).execute(args);
        System.exit(code);
    }

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }
}
