package io.github.dgviz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import picocli.CommandLine;

import java.util.Set;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class DgvizBootApplication {

    private static final Set<String> CLI_COMMANDS = Set.of(
            "analyze", "visualize", "report", "ci-check", "-h", "--help", "-V", "--version"
    );

    public static void main(String[] args) {
        if (args != null && args.length > 0 && CLI_COMMANDS.contains(args[0])) {
            System.exit(new CommandLine(new DgvizApplication()).execute(args));
            return;
        }
        SpringApplication.run(DgvizBootApplication.class, args);
    }
}
