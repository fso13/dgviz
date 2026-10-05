package io.github.dgviz.group;

import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final AppGroupRepository groupRepository;
    private final AppUserRepository userRepository;

    public GroupService(AppGroupRepository groupRepository, AppUserRepository userRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    public List<AppGroup> findAll() {
        return groupRepository.findAllWithMembers();
    }

    public AppGroup getById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
    }

    @Transactional
    public AppGroup create(String name, String description, List<Long> memberIds) {
        if (groupRepository.existsByName(name)) {
            throw new IllegalArgumentException("Group already exists: " + name);
        }
        AppGroup group = new AppGroup();
        group.setName(name);
        group.setDescription(description);
        group.setMembers(resolveMembers(memberIds));
        return groupRepository.save(group);
    }

    @Transactional
    public AppGroup update(Long id, String name, String description, List<Long> memberIds) {
        AppGroup group = getById(id);
        group.setName(name);
        group.setDescription(description);
        group.setMembers(resolveMembers(memberIds));
        return groupRepository.save(group);
    }

    @Transactional
    public void delete(Long id) {
        groupRepository.deleteById(id);
    }

    private Set<AppUser> resolveMembers(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(userRepository.findAllById(memberIds));
    }
}
