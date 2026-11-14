package com.example.hrsblog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * 静态资源链接表实体。
 *
 * <p>只保存「存储 key + 对外链接 + 元数据」，文件本体由
 * {@code StaticStorageService} 落到本地目录或对象存储。
 * 字段与 {@code V2__create_static_asset.sql} 严格一致，满足 {@code ddl-auto: validate}。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "static_asset")
public class StaticAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 相对存储根目录的路径，同时也是唯一键，天然按内容寻址。 */
    @Column(name = "storage_key", nullable = false, unique = true, length = 300)
    private String storageKey;

    /** 对外访问链接，由 public-base-url + storageKey 生成。 */
    @Column(nullable = false, length = 500)
    private String url;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /** SHA-256 十六进制摘要，用于秒传与去重。 */
    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetKind kind = AssetKind.IMAGE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetStatus status = AssetStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP(3)")
    private LocalDateTime updatedAt;

    public enum AssetKind { IMAGE }

    public enum AssetStatus { ACTIVE, DELETED }
}
