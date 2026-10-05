package io.github.dgviz.analysis.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnalysisDependencyRepository extends JpaRepository<AnalysisDependency, Long> {

    List<AnalysisDependency> findByAnalysisRunIdOrderByArtifactIdAsc(Long analysisRunId);

    @Query("""
            SELECT d FROM AnalysisDependency d
            WHERE d.analysisRun.id = :runId
              AND (d.parentNodeId IS NULL OR d.parentNodeId = '')
            ORDER BY d.artifactId
            """)
    List<AnalysisDependency> findRoots(@Param("runId") Long runId);

    List<AnalysisDependency> findByAnalysisRunIdAndParentNodeIdOrderByArtifactIdAsc(Long runId, String parentNodeId);

    @Query("""
            SELECT DISTINCT CONCAT(d.groupId, ':', d.artifactId)
            FROM AnalysisDependency d
            WHERE d.groupId IS NOT NULL AND d.groupId <> ''
              AND d.artifactId IS NOT NULL AND d.artifactId <> ''
            ORDER BY 1
            """)
    List<String> findDistinctMavenCoordinates();
}
