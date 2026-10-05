package io.github.dgviz.security;

import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.AppUserRepository;
import io.github.dgviz.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public DbUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new User(
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled(),
                true,
                true,
                true,
                authorities(user)
        );
    }

    private static List<GrantedAuthority> authorities(AppUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        if (user.getRole() == UserRole.ADMIN) {
            authorities.add(new SimpleGrantedAuthority(PermissionAuthorities.PROJECT_MANAGE));
            authorities.add(new SimpleGrantedAuthority(PermissionAuthorities.REPO_MANAGE));
            authorities.add(new SimpleGrantedAuthority(PermissionAuthorities.GROUP_MANAGE));
            return authorities;
        }
        if (user.isCanManageProjects()) {
            authorities.add(new SimpleGrantedAuthority(PermissionAuthorities.PROJECT_MANAGE));
        }
        if (user.isCanManageRepositories()) {
            authorities.add(new SimpleGrantedAuthority(PermissionAuthorities.REPO_MANAGE));
        }
        if (user.isCanManageGroups()) {
            authorities.add(new SimpleGrantedAuthority(PermissionAuthorities.GROUP_MANAGE));
        }
        return authorities;
    }
}
