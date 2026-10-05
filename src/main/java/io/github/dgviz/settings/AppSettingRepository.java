package io.github.dgviz.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppSettingRepository extends JpaRepository<AppSetting, Long> {

    Optional<AppSetting> findByKey(String key);

    boolean existsByKey(String key);

    List<AppSetting> findAllByOrderByKeyAsc();
}
