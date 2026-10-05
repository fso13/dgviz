package io.github.dgviz.security;

import io.github.dgviz.group.AppGroup;
import io.github.dgviz.group.AppGroupRepository;
import io.github.dgviz.project.Project;
import io.github.dgviz.project.ProjectRepository;
import io.github.dgviz.repository.CodeRepositoryRepository;
import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.AppUserRepository;
import io.github.dgviz.user.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private AppUserRepository userRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private CodeRepositoryRepository codeRepositoryRepository;
    @Mock
    private AppGroupRepository groupRepository;

    @InjectMocks
    private AccessControlService accessControl;

    @Test
    @DisplayName("USER can access project only via group membership")
    void shouldAllowUserOnlyWhenGroupMember() {
        AppUser user = user(10L, UserRole.USER);
        AppUser other = user(11L, UserRole.USER);

        AppGroup group = new AppGroup();
        group.setId(1L);
        group.setMembers(Set.of(user));

        Project project = new Project();
        project.setId(5L);
        project.setGroup(group);

        assertThat(accessControl.canAccessProject(user, project)).isTrue();
        assertThat(accessControl.canAccessProject(other, project)).isFalse();
    }

    @Test
    @DisplayName("USER cannot access project without group")
    void shouldDenyWhenNoGroup() {
        AppUser user = user(10L, UserRole.USER);
        Project project = new Project();
        project.setId(5L);
        project.setGroup(null);

        assertThat(accessControl.canAccessProject(user, project)).isFalse();
    }

    @Test
    @DisplayName("ADMIN can access any project")
    void shouldAllowAdminAlways() {
        AppUser admin = user(1L, UserRole.ADMIN);
        Project project = new Project();
        project.setId(5L);
        project.setGroup(null);

        assertThat(accessControl.canAccessProject(admin, project)).isTrue();
    }

    @Test
    @DisplayName("REPO_MANAGE allows create only in accessible projects")
    void shouldAllowRepoCreateOnlyInOwnProjects() {
        AppUser manager = user(10L, UserRole.USER);
        manager.setCanManageRepositories(true);

        AppGroup group = new AppGroup();
        group.setId(1L);
        group.setMembers(Set.of(manager));

        Project own = new Project();
        own.setId(5L);
        own.setGroup(group);

        Project foreign = new Project();
        foreign.setId(6L);
        AppGroup otherGroup = new AppGroup();
        otherGroup.setId(2L);
        otherGroup.setMembers(Set.of(user(99L, UserRole.USER)));
        foreign.setGroup(otherGroup);

        assertThat(accessControl.canCreateRepositoryIn(manager, own)).isTrue();
        assertThat(accessControl.canCreateRepositoryIn(manager, foreign)).isFalse();
    }

    @Test
    @DisplayName("GROUP_MANAGE works only for groups the user belongs to")
    void shouldAllowGroupManageOnlyForOwnGroups() {
        AppUser manager = user(10L, UserRole.USER);
        manager.setCanManageGroups(true);

        AppGroup own = new AppGroup();
        own.setId(1L);
        own.setMembers(Set.of(manager));

        AppGroup foreign = new AppGroup();
        foreign.setId(2L);
        foreign.setMembers(Set.of(user(99L, UserRole.USER)));

        assertThat(accessControl.canManageGroup(manager, own)).isTrue();
        assertThat(accessControl.canManageGroup(manager, foreign)).isFalse();
    }

    private static AppUser user(Long id, UserRole role) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setUsername("u" + id);
        u.setRole(role);
        return u;
    }
}
