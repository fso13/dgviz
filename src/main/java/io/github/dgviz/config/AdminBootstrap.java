package io.github.dgviz.config;

import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.AppUserRepository;
import io.github.dgviz.user.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DgvizProperties properties;

    public AdminBootstrap(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            DgvizProperties properties
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.getAdmin().getDefaultUsername();
        if (userRepository.existsByUsername(username)) {
            return;
        }
        AppUser admin = new AppUser();
        admin.setUsername(username);
        admin.setDisplayName("Administrator");
        admin.setEmail("admin@localhost");
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        admin.setCanManageProjects(true);
        admin.setCanManageRepositories(true);
        admin.setCanManageGroups(true);
        admin.setPasswordHash(passwordEncoder.encode(properties.getAdmin().getDefaultPassword()));
        userRepository.save(admin);
        log.info("Created default admin user '{}'", username);
    }
}
