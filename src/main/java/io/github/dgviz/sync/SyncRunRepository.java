package io.github.dgviz.sync;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findTop20ByOrderByStartedAtDesc();
}
