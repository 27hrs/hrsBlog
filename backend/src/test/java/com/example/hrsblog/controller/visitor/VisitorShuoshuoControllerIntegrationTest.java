package com.example.hrsblog.controller.visitor;

import com.example.hrsblog.domain.Shuoshuo;
import com.example.hrsblog.repository.ShuoshuoRepository;
import com.example.hrsblog.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 访客说说接口集成测试：可见范围（仅 PUBLIC）、分页与排序。
 *
 * <p>核心安全断言：访客响应体里<b>绝不出现</b> visibility / createdAt / updatedAt，
 * 草稿与编辑痕迹一律不外泄。
 */
class VisitorShuoshuoControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String SHUOSHUO_URL = "/api/visitors/shuoshuo";

    @Autowired
    private ShuoshuoRepository shuoshuoRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void publicListOnlyContainsPublishedShuoshuo() throws Exception {
        createShuoshuo("公开内容", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS));
        createShuoshuo("草稿内容", Shuoshuo.Visibility.PRIVATE, null);

        // 清空一级缓存：让列表查询重新查库（数据库默认值才会被回填）
        entityManager.clear();

        mockMvc.perform(get(SHUOSHUO_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].content").value("公开内容"))
                .andExpect(jsonPath("$.data.items[0].publishedAt").exists());
    }

    @Test
    void publicResponseNeverExposesVisibilityOrTimestamps() throws Exception {
        createShuoshuo("公开内容", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS));

        mockMvc.perform(get(SHUOSHUO_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].visibility").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].updatedAt").doesNotExist());
    }

    @Test
    void publicListSortsByPublishedAtDesc() throws Exception {
        createShuoshuo("最早的一条", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MILLIS));
        createShuoshuo("最新的一条", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        createShuoshuo("中间的一条", Shuoshuo.Visibility.PUBLIC,
                LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS));

        entityManager.clear();

        mockMvc.perform(get(SHUOSHUO_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.items[0].content").value("最新的一条"))
                .andExpect(jsonPath("$.data.items[1].content").value("中间的一条"))
                .andExpect(jsonPath("$.data.items[2].content").value("最早的一条"));
    }

    @Test
    void publicListSupportsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            createShuoshuo("内容 " + i, Shuoshuo.Visibility.PUBLIC,
                    LocalDateTime.now().minusHours(i).truncatedTo(ChronoUnit.MILLIS));
        }

        entityManager.clear();

        mockMvc.perform(get(SHUOSHUO_URL + "?page=0&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        mockMvc.perform(get(SHUOSHUO_URL + "?page=2&size=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(1));
    }

    @Test
    void rejectsInvalidPaginationParameters() throws Exception {
        mockMvc.perform(get(SHUOSHUO_URL + "?page=-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(get(SHUOSHUO_URL + "?size=51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void publicListIsEmptyWhenNothingPublished() throws Exception {
        createShuoshuo("草稿内容", Shuoshuo.Visibility.PRIVATE, null);

        entityManager.clear();

        mockMvc.perform(get(SHUOSHUO_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(0));
    }

    // ── 辅助 ──────────────────────────────────────────────

    /** 造一条说说记录：显式给出可见性与发布时间，便于验证排序。 */
    private Shuoshuo createShuoshuo(String content, Shuoshuo.Visibility visibility, LocalDateTime publishedAt) {
        Shuoshuo shuoshuo = new Shuoshuo();
        shuoshuo.setContent(content);
        shuoshuo.setVisibility(visibility);
        shuoshuo.setPublishedAt(publishedAt);
        return shuoshuoRepository.saveAndFlush(shuoshuo);
    }
}
