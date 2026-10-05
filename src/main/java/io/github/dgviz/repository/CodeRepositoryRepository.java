package io.github.dgviz.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CodeRepositoryRepository extends JpaRepository<CodeRepository, Long> {

    List<CodeRepository> findByProjectIdOrderByNameAsc(Long projectId);

    @Query("SELECT r FROM CodeRepository r JOIN FETCH r.project ORDER BY r.name")
    List<CodeRepository> findAllByOrderByNameAsc();

    @Query("""
            SELECT DISTINCT r FROM CodeRepository r
            JOIN FETCH r.project p
            JOIN p.group g
            JOIN g.members m
            WHERE m.id = :userId
            ORDER BY r.name
            """)
    List<CodeRepository> findAccessibleByUserId(@Param("userId") Long userId);

    @Query("""
            SELECT r FROM CodeRepository r
            JOIN FETCH r.project p
            LEFT JOIN FETCH p.group g
            LEFT JOIN FETCH g.members
            WHERE r.id = :id
            """)
    java.util.Optional<CodeRepository> findByIdWithAccessGraph(@Param("id") Long id);
}
