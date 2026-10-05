package io.github.dgviz.analysis.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StoredAnalysisIssueRepository extends JpaRepository<StoredAnalysisIssue, Long> {

    List<StoredAnalysisIssue> findByAnalysisRunIdOrderBySeverityAscTitleAsc(Long analysisRunId);

    @Query("""
            SELECT i FROM StoredAnalysisIssue i
            JOIN i.analysisRun r
            JOIN r.repository repo
            JOIN repo.project p
            LEFT JOIN p.group g
            LEFT JOIN g.members m
            WHERE i.issueType = 'VULNERABILITY'
              AND (:admin = true OR m.username = :username)
            ORDER BY i.severity ASC, i.title ASC
            """)
    List<StoredAnalysisIssue> findAccessibleVulnerabilities(
            @Param("username") String username,
            @Param("admin") boolean admin
    );
}
