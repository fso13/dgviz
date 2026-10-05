package io.github.dgviz.security;

import io.github.dgviz.group.AppGroup;
import io.github.dgviz.group.AppGroupRepository;
import io.github.dgviz.project.Project;
import io.github.dgviz.project.ProjectRepository;
import io.github.dgviz.repository.CodeRepository;
import io.github.dgviz.repository.CodeRepositoryRepository;
import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.AppUserRepository;
import io.github.dgviz.user.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AccessControlService {

    private final AppUserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final CodeRepositoryRepository codeRepositoryRepository;
    private final AppGroupRepository groupRepository;

    public AccessControlService(
            AppUserRepository userRepository,
            ProjectRepository projectRepository,
            CodeRepositoryRepository codeRepositoryRepository,
            AppGroupRepository groupRepository
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.codeRepositoryRepository = codeRepositoryRepository;
        this.groupRepository = groupRepository;
    }

    public AppUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Not authenticated");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("User not found: " + auth.getName()));
    }

    public boolean isAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN;
    }

    public boolean canManageProjects(AppUser user) {
        return isAdmin(user) || user.isCanManageProjects();
    }

    public boolean canManageRepositories(AppUser user) {
        return isAdmin(user) || user.isCanManageRepositories();
    }

    public boolean canManageGroups(AppUser user) {
        return isAdmin(user) || user.isCanManageGroups();
    }

    public boolean canAccessProject(AppUser user, Project project) {
        if (isAdmin(user)) {
            return true;
        }
        if (project.getGroup() == null) {
            return false;
        }
        return isMember(user, project.getGroup());
    }

    public boolean canAccessRepository(AppUser user, CodeRepository repository) {
        return canAccessProject(user, repository.getProject());
    }

    public boolean canManageGroup(AppUser user, AppGroup group) {
        if (isAdmin(user)) {
            return true;
        }
        return user.isCanManageGroups() && isMember(user, group);
    }

    public boolean canCreateRepositoryIn(AppUser user, Project project) {
        return canManageRepositories(user) && canAccessProject(user, project);
    }

    public boolean canMutateProject(AppUser user, Project project) {
        if (!canManageProjects(user)) {
            return false;
        }
        return isAdmin(user) || canAccessProject(user, project);
    }

    public void requireProjectAccess(Long projectId) {
        getAccessibleProject(projectId);
    }

    public void requireRepositoryAccess(Long repositoryId) {
        getAccessibleRepository(repositoryId);
    }

    public void requireProjectManage() {
        AppUser user = currentUser();
        if (!canManageProjects(user)) {
            throw new AccessDeniedException("No permission to manage projects");
        }
    }

    public void requireRepositoryManage() {
        AppUser user = currentUser();
        if (!canManageRepositories(user)) {
            throw new AccessDeniedException("No permission to manage repositories");
        }
    }

    public void requireGroupManage() {
        AppUser user = currentUser();
        if (!canManageGroups(user)) {
            throw new AccessDeniedException("No permission to manage groups");
        }
    }

    public Project getAccessibleProject(Long projectId) {
        AppUser user = currentUser();
        Project project = projectRepository.findByIdWithAccessGraph(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!canAccessProject(user, project)) {
            throw new AccessDeniedException("No access to project " + projectId);
        }
        return project;
    }

    public CodeRepository getAccessibleRepository(Long repositoryId) {
        AppUser user = currentUser();
        CodeRepository repo = codeRepositoryRepository.findByIdWithAccessGraph(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
        if (!canAccessRepository(user, repo)) {
            throw new AccessDeniedException("No access to repository " + repositoryId);
        }
        return repo;
    }

    public void requireCreateRepositoryIn(Long projectId) {
        AppUser user = currentUser();
        Project project = projectRepository.findByIdWithAccessGraph(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!canCreateRepositoryIn(user, project)) {
            throw new AccessDeniedException(
                    "Cannot create repository in project " + projectId
                            + " — need REPO_MANAGE and membership in the project's group");
        }
    }

    public void requireMutateProject(Long projectId) {
        AppUser user = currentUser();
        Project project = projectRepository.findByIdWithAccessGraph(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        if (!canMutateProject(user, project)) {
            throw new AccessDeniedException("No permission to change project " + projectId);
        }
    }

    public void requireManageGroup(Long groupId) {
        AppUser user = currentUser();
        AppGroup group = groupRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!canManageGroup(user, group)) {
            throw new AccessDeniedException("No permission to manage group " + groupId);
        }
    }

    public void requireAssignableGroup(Long groupId) {
        if (groupId == null) {
            AppUser user = currentUser();
            if (!isAdmin(user)) {
                throw new AccessDeniedException("Non-admin must assign a group they belong to");
            }
            return;
        }
        requireGroupMembership(groupId);
    }

    public void requireGroupMembership(Long groupId) {
        AppUser user = currentUser();
        if (isAdmin(user)) {
            return;
        }
        AppGroup group = groupRepository.findByIdWithMembers(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        if (!isMember(user, group)) {
            throw new AccessDeniedException("You are not a member of group " + groupId);
        }
    }

    public List<Project> accessibleProjects() {
        AppUser user = currentUser();
        if (isAdmin(user)) {
            return projectRepository.findAllByOrderByNameAsc();
        }
        return projectRepository.findAccessibleByUserId(user.getId());
    }

    public List<CodeRepository> accessibleRepositories() {
        AppUser user = currentUser();
        if (isAdmin(user)) {
            return codeRepositoryRepository.findAllByOrderByNameAsc();
        }
        return codeRepositoryRepository.findAccessibleByUserId(user.getId());
    }

    public List<AppGroup> manageableGroups() {
        AppUser user = currentUser();
        if (isAdmin(user)) {
            return groupRepository.findAllWithMembers();
        }
        return groupRepository.findByMemberIdWithMembers(user.getId());
    }

    public List<CodeRepository> repositoriesForProject(Long projectId) {
        getAccessibleProject(projectId);
        return codeRepositoryRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    /**
     * Enabled users with access to the repository who have an email set:
     * project group members (access ACL) — deduplicated, sorted.
     */
    public List<AppUser> usersWithRepositoryAccessEmail(CodeRepository repository) {
        if (repository == null || repository.getProject() == null || repository.getProject().getGroup() == null) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<AppUser> out = new ArrayList<>();
        for (AppUser member : repository.getProject().getGroup().getMembers()) {
            if (member == null || !member.isEnabled()) {
                continue;
            }
            String email = member.getEmail();
            if (email == null || email.isBlank() || !email.contains("@")) {
                continue;
            }
            if (!seen.add(member.getId())) {
                continue;
            }
            out.add(member);
        }
        out.sort(Comparator.comparing(AppUser::getUsername, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<String> emailsWithRepositoryAccess(CodeRepository repository) {
        return usersWithRepositoryAccessEmail(repository).stream()
                .map(u -> u.getEmail().trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private static boolean isMember(AppUser user, AppGroup group) {
        if (group.getMembers() == null) {
            return false;
        }
        return group.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) {
            super(message);
        }
    }
}
