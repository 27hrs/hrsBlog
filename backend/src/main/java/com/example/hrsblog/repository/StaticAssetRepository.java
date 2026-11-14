package com.example.hrsblog.repository;

import com.example.hrsblog.domain.StaticAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 静态资源仓储：storage_key 唯一，按 key 查重即可实现内容秒传。 */
public interface StaticAssetRepository extends JpaRepository<StaticAsset, Long> {

    Optional<StaticAsset> findByStorageKey(String storageKey);

    Page<StaticAsset> findByStatus(StaticAsset.AssetStatus status, Pageable pageable);

    Page<StaticAsset> findByKind(StaticAsset.AssetKind kind, Pageable pageable);

    Page<StaticAsset> findByKindAndStatus(StaticAsset.AssetKind kind, StaticAsset.AssetStatus status, Pageable pageable);
}
