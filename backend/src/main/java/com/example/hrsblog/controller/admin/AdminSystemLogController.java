package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.domain.SystemLog;
import com.example.hrsblog.dto.SystemLogQuery;
import com.example.hrsblog.dto.SystemLogResponse;
import com.example.hrsblog.repository.SystemLogRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员通用日志接口（需要 ROLE_ADMIN）。
 *
 * <ul>
 *   <li>{@code GET /api/admin/logs}：分页查询通用系统日志，可按 {@code type} 与
 *       {@code status} 筛选，默认按写入时间倒序（最新在前）。</li>
 * </ul>
 *
 * <p>日志表记录的是「系统动作」而不是业务数据：例如部署通知 Webhook 的每一次投递尝试。
 * 写入时已完成脱敏，这里不再二次加工。
 */
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
public class AdminSystemLogController {

    private final SystemLogRepository systemLogRepository;

    @GetMapping
    public ApiResponse<PageResponse<SystemLogResponse>> list(@Valid @ModelAttribute SystemLogQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        boolean hasType = StringUtils.hasText(query.getType());
        boolean hasStatus = StringUtils.hasText(query.getStatus());

        Page<SystemLog> page;
        if (hasType && hasStatus) {
            page = systemLogRepository.findByTypeAndStatus(
                    query.getType().trim(), SystemLog.LogStatus.valueOf(query.getStatus()), pageable);
        } else if (hasType) {
            page = systemLogRepository.findByType(query.getType().trim(), pageable);
        } else if (hasStatus) {
            page = systemLogRepository.findByStatus(SystemLog.LogStatus.valueOf(query.getStatus()), pageable);
        } else {
            page = systemLogRepository.findAll(pageable);
        }

        return ApiResponse.ok(PageResponse.of(page, AdminSystemLogController::toResponse));
    }

    private static SystemLogResponse toResponse(SystemLog entry) {
        return new SystemLogResponse(
                entry.getId(),
                entry.getType(),
                entry.getScene(),
                entry.getStatus() == null ? null : entry.getStatus().name(),
                entry.getAttempt(),
                entry.getMessage(),
                entry.getDetail(),
                entry.getCreatedAt());
    }
}
