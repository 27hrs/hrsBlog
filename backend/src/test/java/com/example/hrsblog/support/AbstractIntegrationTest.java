package com.example.hrsblog.support;

import com.example.hrsblog.config.StorageProperties;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.Article;
import com.example.hrsblog.domain.FriendLink;
import com.example.hrsblog.domain.GuestMessage;
import com.example.hrsblog.domain.MessageReply;
import com.example.hrsblog.domain.Tag;
import com.example.hrsblog.repository.AdminUserRepository;
import com.example.hrsblog.repository.ArticleRepository;
import com.example.hrsblog.repository.FriendLinkRepository;
import com.example.hrsblog.repository.GuestMessageRepository;
import com.example.hrsblog.repository.MessageReplyRepository;
import com.example.hrsblog.repository.StaticAssetRepository;
import com.example.hrsblog.repository.TagRepository;
import com.example.hrsblog.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 集成测试基类：test profile（H2 + 关闭 Flyway）+ MockMvc + 事务回滚。
 *
 * <p>每个测试方法都在事务内执行并回滚，因此各用例之间互不影响；
 * 真实 JWT 由被测组件 {@link JwtTokenProvider} 签发，鉴权链路（过滤器 → 安全配置）得到完整覆盖。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_PASSWORD = "Password123";
    protected static final String AUTH_HEADER = "Authorization";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected AdminUserRepository adminUserRepository;

    @Autowired
    protected ArticleRepository articleRepository;

    @Autowired
    protected TagRepository tagRepository;

    @Autowired
    protected FriendLinkRepository friendLinkRepository;

    @Autowired
    protected GuestMessageRepository guestMessageRepository;

    @Autowired
    protected MessageReplyRepository messageReplyRepository;

    @Autowired
    protected StaticAssetRepository staticAssetRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;

    @Autowired
    protected StorageProperties storageProperties;

    protected AdminUser createAdmin(String username) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setDisplayName("Admin " + username);
        admin.setStatus(AdminUser.AdminStatus.ACTIVE);
        return adminUserRepository.saveAndFlush(admin);
    }

    protected String bearer(AdminUser admin) {
        return "Bearer " + jwtTokenProvider.issue(admin).token();
    }

    protected Tag createTag(String name, String slug) {
        Tag tag = new Tag();
        tag.setName(name);
        tag.setSlug(slug);
        return tagRepository.saveAndFlush(tag);
    }

    protected Article createArticle(String slug, Article.ArticleStatus status, LocalDateTime publishedAt, Tag... tags) {
        Article article = new Article();
        article.setTitle("Title " + slug);
        article.setSlug(slug);
        article.setSummary("Summary " + slug);
        article.setContent("# Content " + slug);
        article.setContentFormat("MARKDOWN");
        article.setStatus(status);
        article.setPublishedAt(status == Article.ArticleStatus.PUBLISHED ? publishedAt : null);
        article.setTags(new LinkedHashSet<>(List.of(tags)));
        return articleRepository.saveAndFlush(article);
    }

    protected Path storageRoot() {
        return Path.of(storageProperties.localDir()).toAbsolutePath().normalize();
    }

    /**
     * 造一条友链记录：默认带上联系方式，便于验证「管理端可见、访客端不可见」。
     */
    protected FriendLink createFriendLink(String siteName, String siteUrl, FriendLink.FriendLinkStatus status) {
        FriendLink friendLink = new FriendLink();
        friendLink.setSiteName(siteName);
        friendLink.setSiteUrl(siteUrl);
        friendLink.setLinkPageUrl(siteUrl + "/links");
        friendLink.setDescription("Description " + siteName);
        friendLink.setContact("contact-" + siteName + "@example.test");
        friendLink.setStatus(status);
        friendLink.setSortOrder(0);
        friendLink.setSubmittedIp("203.0.113.10");
        friendLink.setSubmittedAt(LocalDateTime.now());
        return friendLinkRepository.saveAndFlush(friendLink);
    }

    /**
     * 造一条留言记录：默认带上邮箱与来源 IP，便于验证「管理端可见、访客端不可见」。
     */
    protected GuestMessage createGuestMessage(String nickname, String email,
                                              GuestMessage.MessageStatus status) {
        GuestMessage message = new GuestMessage();
        message.setNickname(nickname);
        message.setEmail(email);
        message.setContent("留言内容 " + nickname);
        message.setIpAddress("203.0.113.20");
        message.setUserAgent("JUnit/1.0");
        message.setStatus(status);
        return guestMessageRepository.saveAndFlush(message);
    }

    /** 给一条留言挂上管理员回复。 */
    protected MessageReply createReply(GuestMessage message, AdminUser admin, String content) {
        MessageReply reply = new MessageReply();
        reply.setMessage(message);
        reply.setAdminUser(admin);
        reply.setContent(content);
        return messageReplyRepository.saveAndFlush(reply);
    }
}
