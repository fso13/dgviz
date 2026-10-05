package io.github.dgviz.config;

import io.github.dgviz.repository.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class ScanTokenBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScanTokenBootstrap.class);

    private final RepositoryService repositoryService;

    public ScanTokenBootstrap(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int n = repositoryService.ensureScanTokens();
        if (n > 0) {
            log.info("Generated scan tokens for {} repositories", n);
        }
    }
}
