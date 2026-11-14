package com.example.hrsblog.security;

import com.example.hrsblog.domain.AdminUser;
import com.example.hrsblog.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理员账号加载：把 {@link AdminUser} 的启用状态映射到 Spring Security 的 {@code enabled}，
 * 这样 JWT 有效但账号被禁用时仍会被拒绝（DISABLED 立即生效，不必等 token 过期）。
 */
@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminUserRepository adminUserRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AdminUser admin = adminUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("管理员账号不存在"));
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .authorities(ROLE_ADMIN)
                .disabled(admin.getStatus() != AdminUser.AdminStatus.ACTIVE)
                .build();
    }
}
