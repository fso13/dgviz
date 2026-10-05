package io.github.dgviz.project;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Query("SELECT p FROM Project p LEFT JOIN FETCH p.group ORDER BY p.name")
    List<Project> findAllByOrderByNameAsc();

    boolean existsByName(String name);

    @Query("""
            SELECT DISTINCT p FROM Project p
            JOIN FETCH p.group g
            JOIN g.members m
            WHERE m.id = :userId
            ORDER BY p.name
            """)
    List<Project> findAccessibleByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Project p
            LEFT JOIN FETCH p.group g
            LEFT JOIN FETCH g.members
            WHERE p.id = :id
            """)
    java.util.Optional<Project> findByIdWithAccessGraph(@Param("id") Long id);
}
