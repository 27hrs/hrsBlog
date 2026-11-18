package com.example.hrsblog.repository;

import com.example.hrsblog.domain.Shuoshuo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 说说仓储。
 *
 * <p>所有查询都是 Spring Data 派生方法，参数通过绑定变量传递，
 * 排序与分页由 {@link Pageable} 控制，杜绝字符串拼接 SQL / JPQL。
 *
 * <p>可见性（{@link Shuoshuo.Visibility}）是唯一的过滤维度：
 * 访客端只查 PUBLIC，管理端可按 PUBLIC / PRIVATE 筛选。
 */
public interface ShuoshuoRepository extends JpaRepository<Shuoshuo, Long> {

    /** 按可见性分页查询；排序由调用方通过 Pageable 指定（访客端：published_at 倒序、id 倒序）。 */
    Page<Shuoshuo> findByVisibility(Shuoshuo.Visibility visibility, Pageable pageable);

    /**
     * 管理端分页查询：按创建时间倒序、同序按 id 倒序。
     *
     * <p>排序直接写在方法名里，调用方传入的 Pageable 不再叠加 Sort，
     * 避免两套排序规则同时生效产生歧义。
     */
    Page<Shuoshuo> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
