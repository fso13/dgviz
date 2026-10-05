package io.github.dgviz.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppGroupRepository extends JpaRepository<AppGroup, Long> {

    Optional<AppGroup> findByName(String name);

    boolean existsByName(String name);

    @Query("SELECT DISTINCT g FROM AppGroup g LEFT JOIN FETCH g.members ORDER BY g.name")
    List<AppGroup> findAllWithMembers();

    @Query("""
            SELECT g FROM AppGroup g
            LEFT JOIN FETCH g.members
            WHERE g.id = :id
            """)
    Optional<AppGroup> findByIdWithMembers(@Param("id") Long id);

    @Query("""
            SELECT DISTINCT g FROM AppGroup g
            JOIN FETCH g.members
            WHERE g.id IN (
                SELECT g2.id FROM AppGroup g2 JOIN g2.members m WHERE m.id = :userId
            )
            ORDER BY g.name
            """)
    List<AppGroup> findByMemberIdWithMembers(@Param("userId") Long userId);
}
