package io.github.dgviz.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    HttpFirewall httpFirewall() {
        return new StrictHttpFirewall();
    }

    @Bean
    WebSecurityCustomizer webSecurityCustomizer(HttpFirewall httpFirewall) {
        return web -> web.httpFirewall(httpFirewall);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        var paths = PathPatternRequestMatcher.withDefaults();
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                paths.matcher("/css/**"),
                                paths.matcher("/js/**"),
                                paths.matcher("/login"),
                                paths.matcher("/invite/**"),
                                paths.matcher("/error"),
                                paths.matcher("/actuator/health"),
                                paths.matcher("/api/v1/**")
                        ).permitAll()
                        .requestMatchers(paths.matcher("/admin/projects/**"))
                        .hasAnyAuthority("ROLE_ADMIN", PermissionAuthorities.PROJECT_MANAGE)
                        .requestMatchers(paths.matcher("/admin/repositories/**"))
                        .hasAnyAuthority("ROLE_ADMIN", PermissionAuthorities.REPO_MANAGE)
                        .requestMatchers(paths.matcher("/admin/groups/**"))
                        .hasAnyAuthority("ROLE_ADMIN", PermissionAuthorities.GROUP_MANAGE)
                        .requestMatchers(paths.matcher("/admin/**")).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll())
                .logout(logout -> logout
                        .logoutRequestMatcher(paths.matcher(HttpMethod.POST, "/logout"))
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers(paths.matcher("/api/v1/**")))
                .build();
    }
}
