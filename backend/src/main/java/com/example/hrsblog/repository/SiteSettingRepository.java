package com.example.hrsblog.repository;

import com.example.hrsblog.domain.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 站点配置仓储。
 *
 * <p>数据库层对 {@code (config_group, config_key)} 建有唯一约束；目录中每个
 * config_key 全局唯一，因此按 key 查重即可完成 upsert。
 */
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {

    Optional<SiteSetting> findByConfigKey(String configKey);

    List<SiteSetting> findAllByOrderByConfigGroupAscSortOrderAsc();

    List<SiteSetting> findByPublicSettingTrueOrderBySortOrderAsc();
}
