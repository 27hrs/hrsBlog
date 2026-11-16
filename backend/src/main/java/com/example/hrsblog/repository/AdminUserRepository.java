package com.example.hrsblog.repository;

import com.example.hrsblog.domain.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 管理员账号仓储。
 *
 * <p>使用 Spring Data 派生查询（参数化绑定），不拼接任何 SQL 字符串。
 */
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 修改用户名时唯一性预检（排除自身 id）。 */
    boolean existsByUsernameAndIdNot(String username, Long id);
}
