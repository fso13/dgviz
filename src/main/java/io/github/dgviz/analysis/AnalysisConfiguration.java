package io.github.dgviz.analysis;

import io.github.dgviz.parser.ParserRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AnalysisConfiguration {

    @Bean
    @Primary
    AnalysisEngine analysisEngine(DatabaseVulnerabilityDetector dbDetector) {
        List<IssueDetector> detectors = new ArrayList<>();
        detectors.add(new ConflictDetector());
        detectors.add(new DuplicateDetector());
        detectors.add(dbDetector);
        detectors.add(new VulnerabilityDetector(0));
        return new AnalysisEngine(ParserRegistry.withDefaults(), detectors);
    }
}
