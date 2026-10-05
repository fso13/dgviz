package io.github.dgviz.repository;

import io.github.dgviz.project.Project;
import io.github.dgviz.project.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class RepositoryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CodeRepositoryRepository repositoryRepository;
    private final ProjectRepository projectRepository;

    public RepositoryService(CodeRepositoryRepository repositoryRepository, ProjectRepository projectRepository) {
        this.repositoryRepository = repositoryRepository;
        this.projectRepository = projectRepository;
    }

    public List<CodeRepository> findAll() {
        return repositoryRepository.findAllByOrderByNameAsc();
    }

    public CodeRepository getById(Long id) {
        return repositoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + id));
    }

    @Transactional
    public CodeRepository create(
            Long projectId,
            String name,
            String sourceType,
            String vcsUrl,
            String gitlabPath,
            String remoteHost,
            String accessToken,
            String defaultBranch,
            String buildSystem,
            boolean createIssuesDefault
    ) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        RepositorySourceType type = RepositorySourceType.from(sourceType);
        CodeRepository repo = new CodeRepository();
        repo.setProject(project);
        repo.setName(name.trim());
        repo.setSourceType(type);
        repo.setVcsUrl(blankToNull(vcsUrl));
        repo.setGitlabPath(blankToNull(gitlabPath));
        repo.setRemoteHost(blankToNull(remoteHost));
        repo.setAccessToken(blankToNull(accessToken));
        repo.setDefaultBranch(defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch.trim());
        repo.setBuildSystem(normalizeBuildSystem(buildSystem));
        repo.setCreateIssuesDefault(createIssuesDefault);
        repo.setScanToken(newScanToken());
        repo.setLocalPath(null);
        validateRemoteFields(repo);
        return repositoryRepository.save(repo);
    }

    @Transactional
    public CodeRepository update(
            Long id,
            Long projectId,
            String name,
            String sourceType,
            String vcsUrl,
            String gitlabPath,
            String remoteHost,
            String accessToken,
            String defaultBranch,
            String buildSystem,
            boolean createIssuesDefault
    ) {
        CodeRepository repo = getById(id);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
        repo.setProject(project);
        repo.setName(name.trim());
        repo.setSourceType(RepositorySourceType.from(sourceType));
        repo.setVcsUrl(blankToNull(vcsUrl));
        repo.setGitlabPath(blankToNull(gitlabPath));
        repo.setRemoteHost(blankToNull(remoteHost));
        if (accessToken != null && !accessToken.isBlank() && !accessToken.contains("••••")) {
            repo.setAccessToken(accessToken.trim());
        }
        repo.setDefaultBranch(defaultBranch == null || defaultBranch.isBlank() ? "main" : defaultBranch.trim());
        repo.setBuildSystem(normalizeBuildSystem(buildSystem));
        repo.setCreateIssuesDefault(createIssuesDefault);
        if (repo.getScanToken() == null || repo.getScanToken().isBlank()) {
            repo.setScanToken(newScanToken());
        }
        validateRemoteFields(repo);
        return repositoryRepository.save(repo);
    }

    @Transactional
    public CodeRepository regenerateScanToken(Long id) {
        CodeRepository repo = getById(id);
        repo.setScanToken(newScanToken());
        return repositoryRepository.save(repo);
    }

    @Transactional
    public int ensureScanTokens() {
        int n = 0;
        for (CodeRepository repo : repositoryRepository.findAll()) {
            if (repo.getScanToken() == null || repo.getScanToken().isBlank()) {
                repo.setScanToken(newScanToken());
                repositoryRepository.save(repo);
                n++;
            }
        }
        return n;
    }

    @Transactional
    public void delete(Long id) {
        repositoryRepository.deleteById(id);
    }

    public static String newScanToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "dgviz_" + HexFormat.of().formatHex(bytes);
    }

    private void validateRemoteFields(CodeRepository repo) {
        if (!repo.getSourceType().isRemote()) {
            return;
        }
        boolean hasUrl = repo.getVcsUrl() != null && !repo.getVcsUrl().isBlank();
        boolean hasPath = repo.getGitlabPath() != null && !repo.getGitlabPath().isBlank();
        if (!hasUrl && !hasPath) {
            throw new IllegalArgumentException("Для GitLab/GitHub укажите VCS URL или path (group/project)");
        }
    }

    private static String normalizeBuildSystem(String buildSystem) {
        if (buildSystem == null || buildSystem.isBlank()) {
            return "gradle";
        }
        String v = buildSystem.trim().toLowerCase();
        if (!v.equals("maven") && !v.equals("gradle")) {
            throw new IllegalArgumentException("buildSystem must be maven or gradle");
        }
        return v;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
