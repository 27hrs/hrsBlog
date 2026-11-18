package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.domain.Shuoshuo;
import com.example.hrsblog.event.ContentChangedEvent;
import com.example.hrsblog.repository.ShuoshuoRepository;
import com.example.hrsblog.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员说说接口集成测试：鉴权、CRUD、发布 / 撤回语义、删除文案与埋点。
 *
 * <p>埋点断言依赖 {@link ContentChangedEventRecorder}：它用普通 {@code @EventListener}
 * 订阅 {@link ContentChangedEvent}，事件在事务内发布时立刻收到，
 * 因此可以断言「哪些操作触发了内容变更通知」。
 */
class AdminShuoshuoControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String SHUOSHUO_URL = "/api/admin/shuoshuo";

    @Autowired
    private ShuoshuoRepository shuoshuoRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ContentChangedEventRecorder eventRecorder;

    private AdminUser admin;
    private String authorization;

    /** 记录内容变更事件：用于断言「哪些操作触发了埋点」。 */
    static class ContentChangedEventRecorder {

        private final List<ContentChangedEvent> events = new CopyOnWriteArrayList<>();

        /**
         * 普通 {@code @EventListener}（不是事务监听器）：事件在事务内发布时立刻回调，
         * 因此可以直接断言「哪些操作触发了内容变更通知」。
         */
        @EventListener
        void onContentChanged(ContentChangedEvent event) {
            events.add(event);
        }

        void clear() {
            events.clear();
        }

        /** 汇总本次用例内收到的全部变更原因。 */
        List<String> reasons() {
            return events.stream().flatMap(event -> event.reasons().stream()).toList();
        }
    }

    /** 把事件记录器注册进测试上下文。 */
    @TestConfiguration
    static class EventRecorderConfiguration {

        @Bean
        ContentChangedEventRecorder contentChangedEventRecorder() {
            return new ContentChangedEventRecorder();
        }
    }

    @BeforeEach
    void setUpAdmin() {
        admin = createAdmin("shuoshuo-admin");
        authorization = bearer(admin);
        eventRecorder.clear();
    }

    // ── 鉴权 ──────────────────────────────────────────────

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get(SHUOSHUO_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void rejectsRequestWithMalformedToken() throws Exception {
        mockMvc.perform(get(SHUOSHUO_URL).header(AUTH_HEADER, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ── 创建 ──────────────────────────────────────────────

    @Test
    void createsShuoshuoAsPrivateDraft() throws Exception {
        mockMvc.perform(post(SHUOSHUO_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"今天写点东西\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.content").value("今天写点东西"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.publishedAt").doesNotExist())
                .andExpect(jsonPath("$.data.id").exists());

        Shuoshuo saved = shuoshuoRepository.findAll().getFirst();
        assertThat(saved.getVisibility()).isEqualTo(Shuoshuo.Visibility.PRIVATE);
        assertThat(saved.getPublishedAt()).isNull();
        // 草稿的创建不触发任何外部通知
        assertThat(eventRecorder.reasons()).isEmpty();
    }

    @Test
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post(SHUOSHUO_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.content").exists());
        assertThat(shuoshuoRepository.count()).isZero();
    }

    @Test
    void rejectsTooLongContent() throws Exception {
        mockMvc.perform(post(SHUOSHUO_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "a".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.data.content").exists());
        assertThat(shuoshuoRepository.count()).isZero();
    }

    // ── 发布 / 撤回 ───────────────────────────────────────

    @Test
    void publishesShuoshuoAndBackfillsPublishedAt() throws Exception {
        Shuoshuo draft = createShuoshuo("草稿内容", Shuoshuo.Visibility.PRIVATE, null);

        mockMvc.perform(post(SHUOSHUO_URL + "/" + draft.getId() + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        assertThat(shuoshuoRepository.findById(draft.getId()).orElseThrow().getPublishedAt()).isNotNull();
        assertThat(eventRecorder.reasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void unpublishKeepsOriginalPublishedAt() throws Exception {
        LocalDateTime originalPublishedAt = LocalDateTime.now().minusDays(3).truncatedTo(ChronoUnit.MILLIS);
        Shuoshuo published = createShuoshuo("公开内容", Shuoshuo.Visibility.PUBLIC, originalPublishedAt);

        mockMvc.perform(post(SHUOSHUO_URL + "/" + published.getId() + "/unpublish")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.data.publishedAt").exists());

        Shuoshuo reloaded = shuoshuoRepository.findById(published.getId()).orElseThrow();
        assertThat(reloaded.getVisibility()).isEqualTo(Shuoshuo.Visibility.PRIVATE);
        // 撤回保留发布时间：重新发布时回到原来的时间线位置
        assertThat(reloaded.getPublishedAt()).isEqualTo(originalPublishedAt);
        assertThat(eventRecorder.reasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void publishesUnknownShuoshuoAsNotFound() throws Exception {
        mockMvc.perform(post(SHUOSHUO_URL + "/999999/publish")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.code()));
    }

    // ── 编辑 ──────────────────────────────────────────────

    @Test
    void updatesPublicShuoshuoAndPublishesEvent() throws Exception {
        Shuoshuo published = createShuoshuo("旧内容", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS));

        mockMvc.perform(put(SHUOSHUO_URL + "/" + published.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("新内容"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"));

        assertThat(shuoshuoRepository.findById(published.getId()).orElseThrow().getContent())
                .isEqualTo("新内容");
        assertThat(eventRecorder.reasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void updatesDraftWithoutPublishingEvent() throws Exception {
        Shuoshuo draft = createShuoshuo("旧内容", Shuoshuo.Visibility.PRIVATE, null);

        mockMvc.perform(put(SHUOSHUO_URL + "/" + draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新内容\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("新内容"));

        // 草稿的编辑不触发任何外部通知
        assertThat(eventRecorder.reasons()).isEmpty();
    }

    @Test
    void updatesUnknownShuoshuoAsNotFound() throws Exception {
        mockMvc.perform(put(SHUOSHUO_URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新内容\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.code()));
    }

    // ── 列表与详情 ───────────────────────────────────────

    @Test
    void listsShuoshuoWithVisibilityFilter() throws Exception {
        createShuoshuo("公开内容", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusHours(2).truncatedTo(ChronoUnit.MILLIS));
        createShuoshuo("草稿内容", Shuoshuo.Visibility.PRIVATE, null);

        // 清空一级缓存：让列表查询重新查库（数据库默认值才会被回填）
        entityManager.clear();

        mockMvc.perform(get(SHUOSHUO_URL + "?visibility=PUBLIC")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("公开内容"))
                .andExpect(jsonPath("$.data.items[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$.data.items[0].createdAt").exists());

        mockMvc.perform(get(SHUOSHUO_URL + "?visibility=PRIVATE")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("草稿内容"));

        mockMvc.perform(get(SHUOSHUO_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void rejectsUnknownVisibilityFilter() throws Exception {
        mockMvc.perform(get(SHUOSHUO_URL + "?visibility=ARCHIVED")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.code()));
    }

    @Test
    void returnsShuoshuoDetail() throws Exception {
        Shuoshuo draft = createShuoshuo("详情内容", Shuoshuo.Visibility.PRIVATE, null);

        mockMvc.perform(get(SHUOSHUO_URL + "/" + draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(draft.getId()))
                .andExpect(jsonPath("$.data.content").value("详情内容"))
                .andExpect(jsonPath("$.data.visibility").value("PRIVATE"));
    }

    @Test
    void returnsNotFoundForUnknownShuoshuo() throws Exception {
        mockMvc.perform(get(SHUOSHUO_URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.code()));
    }

    // ── 删除 ──────────────────────────────────────────────

    @Test
    void deletesShuoshuoWithCustomMessage() throws Exception {
        Shuoshuo published = createShuoshuo("待删除内容", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS));

        mockMvc.perform(delete(SHUOSHUO_URL + "/" + published.getId())
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("说说已删除"))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 读回触发自动 flush：删除语句在同一次查询前落库，随后查不到该行
        assertThat(shuoshuoRepository.findById(published.getId())).isEmpty();
        assertThat(eventRecorder.reasons()).containsExactly(ContentChangedEvent.REASON_SHUOSHUO_UPDATED);
    }

    @Test
    void deletesUnknownShuoshuoAsNotFound() throws Exception {
        mockMvc.perform(delete(SHUOSHUO_URL + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.code()));
    }

    // ── 辅助 ──────────────────────────────────────────────

    /** 造一条说说记录：显式给出可见性与发布时间，便于验证时间线语义。 */
    private Shuoshuo createShuoshuo(String content, Shuoshuo.Visibility visibility, LocalDateTime publishedAt) {
        Shuoshuo shuoshuo = new Shuoshuo();
        shuoshuo.setContent(content);
        shuoshuo.setVisibility(visibility);
        shuoshuo.setPublishedAt(publishedAt);
        return shuoshuoRepository.saveAndFlush(shuoshuo);
    }
}
