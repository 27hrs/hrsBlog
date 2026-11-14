package com.example.hrsblog.repository;

import com.example.hrsblog.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 标签仓储：投递文章时按名称复用已有标签，不存在则新建。 */
public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByName(String name);

    Optional<Tag> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsByName(String name);
}
