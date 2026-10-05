package io.github.dgviz.analysis.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

    List<AnalysisRun> findByRepositoryIdOrderByAnalyzedAtDesc(Long repositoryId);

    Optional<AnalysisRun> findFirstByRepositoryIdOrderByAnalyzedAtDesc(Long repositoryId);

    @Query("""
            SELECT r FROM AnalysisRun r
            JOIN FETCH r.repository repo
            JOIN FETCH repo.project
            WHERE r.id = :id
            """)
    Optional<AnalysisRun> findDetailedById(@Param("id") Long id);
}
