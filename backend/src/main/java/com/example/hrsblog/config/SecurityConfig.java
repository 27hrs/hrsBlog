package com.example.hrsblog.config;

import com.example.hrsblog.security.JwtAuthenticationFilter;
import com.example.hrsblog.security.RestAccessDeniedHandler;
import com.example.hrsblog.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 安全配置：无状态 JWT + 路由分组授权。
 *
 * <ul>
 *   <li>访客接口 {@code /api/visitors/**}、静态资源 {@code /assets/**}、登录接口匿名可访问；</li>
 *   <li>其余 {@code /api/admin/**} 需要 {@code ROLE_ADMIN}；</li>
 *   <li>未匹配的路径默认拒绝（fail closed），避免将来新增接口意外暴露。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/api/admin/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/visitors/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/visitors/**").permitAll()
                        // 匿名写接口：友链申请与留言提交，都走 IP 限频，且不返回联系方式
                        .requestMatchers(HttpMethod.POST, "/api/visitors/friend-links").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/visitors/guest-messages").permitAll()
                        .requestMatchers("/assets/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 使用 {@link PasswordEncoderFactories} 的委托式编码器：默认写入 {@code {bcrypt}} 前缀，
     * 便于将来平滑迁移到更强算法，同时对历史明文/其他算法哈希保持兼容入口。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
