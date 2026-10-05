package io.github.dgviz.project;

import io.github.dgviz.group.AppGroup;
import io.github.dgviz.group.AppGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AppGroupRepository groupRepository;

    public ProjectService(ProjectRepository projectRepository, AppGroupRepository groupRepository) {
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
    }

    public List<Project> findAll() {
        return projectRepository.findAllByOrderByNameAsc();
    }

    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    @Transactional
    public Project create(String name, String description, Long groupId) {
        if (projectRepository.existsByName(name)) {
            throw new IllegalArgumentException("Project already exists: " + name);
        }
        Project project = new Project();
        project.setName(name);
        project.setDescription(description);
        if (groupId != null) {
            AppGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
            project.setGroup(group);
        }
        return projectRepository.save(project);
    }

    @Transactional
    public Project update(Long id, String name, String description, Long groupId) {
        Project project = getById(id);
        project.setName(name);
        project.setDescription(description);
        if (groupId == null) {
            project.setGroup(null);
        } else {
            project.setGroup(groupRepository.findById(groupId)
                    .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId)));
        }
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.deleteById(id);
    }
}
