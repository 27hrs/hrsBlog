-- HrsBlog V2：静态资源链接表。
-- 文件本体存放于本地目录或对象存储，这里只登记「存储 key + 对外链接」等元数据。
-- 字段与 domain/StaticAsset.java 严格一一对应，满足 spring.jpa.hibernate.ddl-auto=validate。

CREATE TABLE static_asset (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '静态资源唯一标识',
    storage_key VARCHAR(300) NOT NULL COMMENT '存储路径/对象 key，相对于存储根目录，唯一',
    url VARCHAR(500) NOT NULL COMMENT '对外访问链接，由 public-base-url + storage_key 生成',
    original_name VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名，仅用于展示与下载命名',
    mime_type VARCHAR(100) NOT NULL COMMENT '文件 MIME 类型，例如 image/png',
    file_size BIGINT UNSIGNED NOT NULL COMMENT '文件字节大小',
    file_hash VARCHAR(64) NULL COMMENT '文件内容的 SHA-256 十六进制摘要，用于秒传去重',
    kind VARCHAR(16) NOT NULL DEFAULT 'IMAGE' COMMENT '资源类别：IMAGE',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '资源状态：ACTIVE 可用，DELETED 已删除',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '资源登记时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '资源最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_static_asset_storage_key (storage_key),
    KEY idx_static_asset_kind_created (kind, created_at),
    KEY idx_static_asset_hash (file_hash),
    CONSTRAINT ck_static_asset_status CHECK (status IN ('ACTIVE', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='静态资源链接表';
