-- HrsBlog V4：友链申请表。
-- 游客在前台提交友链申请，管理端审核；审核通过的友链由构建期同步脚本拉到博客前台展示。
-- 字段与 domain/FriendLink.java 严格一一对应，满足 spring.jpa.hibernate.ddl-auto=validate。
-- 注意：contact（联系方式）只在管理端响应里出现，访客接口绝不返回该列。

CREATE TABLE friend_link (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '友链唯一标识',
    site_name VARCHAR(100) NOT NULL COMMENT '站点名称，前台卡片展示',
    site_url VARCHAR(500) NOT NULL COMMENT '站点地址，点击卡片跳转的外链',
    link_page_url VARCHAR(500) NOT NULL COMMENT '对方站点的友链页地址',
    avatar_url VARCHAR(500) NULL COMMENT '站点图标 / 头像地址，为空时前台回退字母头像',
    description VARCHAR(500) NULL COMMENT '站点简介，前台卡片展示',
    contact VARCHAR(200) NULL COMMENT '联系方式，仅管理员可见，访客接口不返回',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING 待审核，APPROVED 已通过，REJECTED 已拒绝',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '前台展示顺序，数值越小越靠前',
    submitted_ip VARCHAR(45) NULL COMMENT '提交来源 IP，兼容 IPv4 与 IPv6，用于按 IP 限频',
    submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
    reviewed_at DATETIME(3) NULL COMMENT '审核时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '记录创建时间',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '记录最后修改时间',
    PRIMARY KEY (id),
    KEY idx_friend_link_status_sort (status, sort_order),
    KEY idx_friend_link_status_submitted (status, submitted_at),
    KEY idx_friend_link_ip_submitted (submitted_ip, submitted_at),
    CONSTRAINT ck_friend_link_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='友链申请表';
