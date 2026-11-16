package com.example.hrsblog;

import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.FriendLink;
import com.example.hrsblog.domain.StaticAsset;
import com.example.hrsblog.domain.Tag;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.FriendLinkRepository;
import com.example.hrsblog.repository.StaticAssetRepository;
import com.example.hrsblog.repository.TagRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 持久化冒烟测试：确认实体映射与测试库建表一致，
 * 且 created_at / updated_at 由数据库默认值填充（与 V1 / V2 迁移中的 DEFAULT 等价）。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PersistenceSmokeTest {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private StaticAssetRepository staticAssetRepository;

    @Autowired
    private FriendLinkRepository friendLinkRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void timestampsAreFilledByDatabaseDefaults() {
        AdminUser admin = new AdminUser();
        admin.setUsername("smoke-admin");
        admin.setPasswordHash("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");
        admin.setDisplayName("Smoke Admin");
        adminUserRepository.saveAndFlush(admin);
        entityManager.refresh(admin);
        assertThat(admin.getCreatedAt()).isNotNull();
        assertThat(admin.getUpdatedAt()).isNotNull();

        Tag tag = new Tag();
        tag.setName("Java");
        tag.setSlug("java");
        tagRepository.saveAndFlush(tag);

        Article article = new Article();
        article.setTitle("Smoke Article");
        article.setSlug("smoke-article");
        article.setContent("# hello");
        article.setStatus(Article.ArticleStatus.PUBLISHED);
        article.getTags().add(tag);
        articleRepository.saveAndFlush(article);
        entityManager.refresh(article);
        assertThat(article.getCreatedAt()).isNotNull();
        assertThat(article.getTags()).extracting(Tag::getSlug).containsExactly("java");

        StaticAsset asset = new StaticAsset();
        asset.setStorageKey("image/2026/01/smoke.png");
        asset.setUrl("https://cdn.test/assets/image/2026/01/smoke.png");
        asset.setOriginalName("smoke.png");
        asset.setMimeType("image/png");
        asset.setFileSize(68L);
        asset.setFileHash("0".repeat(64));
        staticAssetRepository.saveAndFlush(asset);
        entityManager.refresh(asset);
        assertThat(asset.getCreatedAt()).isNotNull();
        assertThat(asset.getStatus()).isEqualTo(StaticAsset.AssetStatus.ACTIVE);
        assertThat(asset.getKind()).isEqualTo(StaticAsset.AssetKind.IMAGE);

        FriendLink friendLink = new FriendLink();
        friendLink.setSiteName("Smoke Site");
        friendLink.setSiteUrl("https://smoke.test");
        friendLink.setLinkPageUrl("https://smoke.test/links");
        friendLink.setDescription("Smoke friend link");
        friendLink.setContact("smoke@smoke.test");
        friendLink.setStatus(FriendLink.FriendLinkStatus.PENDING);
        friendLink.setSortOrder(0);
        friendLink.setSubmittedIp("203.0.113.10");
        friendLink.setSubmittedAt(LocalDateTime.now());
        friendLinkRepository.saveAndFlush(friendLink);
        entityManager.refresh(friendLink);
        assertThat(friendLink.getCreatedAt()).isNotNull();
        assertThat(friendLink.getUpdatedAt()).isNotNull();
        assertThat(friendLink.getStatus()).isEqualTo(FriendLink.FriendLinkStatus.PENDING);
    }
}
