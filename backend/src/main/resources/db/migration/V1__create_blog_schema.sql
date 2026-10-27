-- HrsBlog initial schema.
-- MySQL 8.0+, InnoDB, utf8mb4. All comments describe the purpose of each field.

CREATE TABLE admin_user (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '管理员唯一标识',
    username VARCHAR(64) NOT NULL COMMENT '管理员登录账号，必须唯一',
    password_hash VARCHAR(255) NOT NULL COMMENT '使用 BCrypt 等算法生成的密码哈希，禁止保存明文密码',
    display_name VARCHAR(64) NOT NULL COMMENT '管理后台和回复留言时显示的名称',
    avatar_url VARCHAR(500) NULL COMMENT '管理员头像地址',
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE 可登录，DISABLED 禁止登录',
    last_login_at DATETIME(3) NULL COMMENT '最近一次成功登录时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '管理员账号创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '管理员账号最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_user_username (username),
    CONSTRAINT ck_admin_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='管理员账号配置表';

CREATE TABLE article (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '文章唯一标识',
    title VARCHAR(200) NOT NULL COMMENT '文章标题',
    slug VARCHAR(220) NOT NULL COMMENT '文章 URL 标识，例如 spring-boot-guide，必须唯一',
    summary VARCHAR(500) NULL COMMENT '文章摘要，用于列表页、SEO 描述或推荐卡片',
    content LONGTEXT NOT NULL COMMENT '文章正文内容',
    content_format VARCHAR(16) NOT NULL DEFAULT 'MARKDOWN' COMMENT '正文格式，首版支持 MARKDOWN',
    cover_url VARCHAR(500) NULL COMMENT '文章封面图片地址',
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' COMMENT '文章状态：DRAFT 草稿，PUBLISHED 已发布',
    published_at DATETIME(3) NULL COMMENT '文章正式发布时间，草稿时为空',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '文章创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '文章最后编辑时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_article_slug (slug),
    KEY idx_article_status_published (status, published_at),
    KEY idx_article_created (created_at),
    CONSTRAINT ck_article_content_format CHECK (content_format IN ('MARKDOWN')),
    CONSTRAINT ck_article_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='博客文章表';

CREATE TABLE tag (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签唯一标识',
    name VARCHAR(50) NOT NULL COMMENT '前台展示的标签名称，例如 Java',
    slug VARCHAR(80) NOT NULL COMMENT '标签 URL 标识，例如 java，必须唯一',
    description VARCHAR(255) NULL COMMENT '标签描述或简介',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '标签创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '标签最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name),
    UNIQUE KEY uk_tag_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='文章标签表';

CREATE TABLE article_tag (
    article_id BIGINT UNSIGNED NOT NULL COMMENT '关联的文章 ID',
    tag_id BIGINT UNSIGNED NOT NULL COMMENT '关联的标签 ID',
    PRIMARY KEY (article_id, tag_id),
    CONSTRAINT fk_article_tag_article FOREIGN KEY (article_id) REFERENCES article (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_article_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='文章与标签的多对多关联表';

CREATE TABLE guest_message (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '留言唯一标识',
    nickname VARCHAR(64) NOT NULL COMMENT '留言者展示名称',
    email VARCHAR(255) NULL COMMENT '留言者邮箱，可用于回复或通知，不在前台公开',
    website VARCHAR(500) NULL COMMENT '留言者个人网站地址',
    content TEXT NOT NULL COMMENT '留言正文',
    ip_address VARCHAR(45) NULL COMMENT '留言来源 IP，兼容 IPv4 和 IPv6，用于审核和防刷',
    user_agent VARCHAR(500) NULL COMMENT '留言者浏览器信息，用于安全审计',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING 待审核，APPROVED 已通过，REJECTED 已拒绝',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '留言提交时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '留言审核或编辑时间',
    PRIMARY KEY (id),
    KEY idx_guest_message_status_created (status, created_at),
    KEY idx_guest_message_created (created_at),
    CONSTRAINT ck_guest_message_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='游客留言板表';

CREATE TABLE message_reply (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '回复唯一标识',
    message_id BIGINT UNSIGNED NOT NULL COMMENT '被回复的游客留言 ID',
    admin_user_id BIGINT UNSIGNED NOT NULL COMMENT '发布回复的管理员 ID',
    content TEXT NOT NULL COMMENT '管理员回复内容',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '回复创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '回复最后修改时间',
    PRIMARY KEY (id),
    KEY idx_message_reply_message (message_id),
    KEY idx_message_reply_admin (admin_user_id),
    CONSTRAINT fk_message_reply_message FOREIGN KEY (message_id) REFERENCES guest_message (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_message_reply_admin FOREIGN KEY (admin_user_id) REFERENCES admin_user (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='管理员留言回复表';

CREATE TABLE shuoshuo (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '说说唯一标识',
    content TEXT NOT NULL COMMENT '说说正文',
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE' COMMENT '可见状态：PUBLIC 公开，PRIVATE 私有',
    published_at DATETIME(3) NULL COMMENT '说说发布时间，私有内容可以为空',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '说说创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '说说最后编辑时间',
    PRIMARY KEY (id),
    KEY idx_shuoshuo_visibility_published (visibility, published_at),
    CONSTRAINT ck_shuoshuo_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='说说动态表';

CREATE TABLE site_setting (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '配置记录唯一标识',
    config_group VARCHAR(64) NOT NULL COMMENT '配置分组，例如 basic、social、seo',
    config_key VARCHAR(128) NOT NULL COMMENT '配置名称，例如 site_name、github_url',
    config_value TEXT NOT NULL COMMENT '配置实际值，统一以字符串形式存储',
    value_type VARCHAR(16) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING、NUMBER、BOOLEAN、JSON，用于读取时转换',
    description VARCHAR(255) NULL COMMENT '配置用途说明，便于后台管理',
    is_public BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许返回给博客前台，敏感配置必须为 FALSE',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '同一配置分组内的展示顺序',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '配置创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '配置最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_site_setting_group_key (config_group, config_key),
    KEY idx_site_setting_public_order (is_public, config_group, sort_order),
    CONSTRAINT ck_site_setting_value_type CHECK (value_type IN ('STRING', 'NUMBER', 'BOOLEAN', 'JSON'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='站点键值配置表';
