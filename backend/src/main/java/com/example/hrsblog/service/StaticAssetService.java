package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.common.HashUtils;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.config.StorageProperties;
import com.example.hrsblog.domain.StaticAsset;
import com.example.hrsblog.dto.AssetQuery;
import com.example.hrsblog.dto.AssetResponse;
import com.example.hrsblog.repository.StaticAssetRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 静态资源管理：上传校验 → 内容寻址生成存储 key → 落盘 → 落库 → 返回链接。
 *
 * <p>校验分三层，任何一层不通过都返回 415：
 * <ol>
 *   <li>扩展名白名单（jpg/jpeg/png/webp/gif，可配置）；</li>
 *   <li>声明的 Content-Type 必须与扩展名匹配（如 jpg 只接受 image/jpeg）；</li>
 *   <li>文件头魔数必须与扩展名一致，防止把可执行内容伪装成图片。</li>
 * </ol>
 *
 * <p>去重：存储 key 由内容 SHA-256 派生，同一份内容重复上传只会落一条记录、一个文件。
 */
@Slf4j
@Service
public class StaticAssetService {

    private static final Map<String, String> MIME_BY_EXTENSION = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif");

    private final StaticAssetRepository staticAssetRepository;
    private final StaticStorageService storageService;
    private final StorageProperties storageProperties;
    private final EntityManager entityManager;
    private final Set<String> allowedTypes;

    public StaticAssetService(StaticAssetRepository staticAssetRepository,
                              StaticStorageService storageService,
                              StorageProperties storageProperties,
                              EntityManager entityManager) {
        this.staticAssetRepository = staticAssetRepository;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.entityManager = entityManager;
        this.allowedTypes = storageProperties.allowedTypes().stream()
                .filter(StringUtils::hasText)
                .map(extension -> extension.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Transactional
    public AssetResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        long maxBytes = storageProperties.maxSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw tooLarge(maxBytes);
        }

        String originalName = sanitizeOriginalName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        if (!allowedTypes.contains(extension)) {
            throw notAllowed("文件扩展名不在允许列表内");
        }
        String expectedMime = MIME_BY_EXTENSION.get(extension);
        if (expectedMime == null) {
            throw notAllowed("文件扩展名不在允许列表内");
        }
        String declaredMime = normalizeMimeType(file.getContentType());
        if (!expectedMime.equals(declaredMime)) {
            throw notAllowed("Content-Type 与文件扩展名不匹配");
        }

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "读取上传文件失败", ex);
        }
        if (content.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (content.length > maxBytes) {
            throw tooLarge(maxBytes);
        }
        String detectedMime = sniffImageMime(content);
        if (!expectedMime.equals(detectedMime)) {
            throw notAllowed("文件内容与扩展名不匹配，只接受真实图片");
        }

        String hash = HashUtils.sha256Hex(content);
        String storageKey = buildStorageKey(hash, extension);
        String url = storageService.urlOf(storageKey);

        StaticAsset existing = staticAssetRepository.findByStorageKey(storageKey).orElse(null);
        if (existing != null && existing.getStatus() == StaticAsset.AssetStatus.ACTIVE) {
            // 内容秒传：文件被外部清理时顺带补写，保证链接仍可用
            if (!storageService.exists(storageKey)) {
                storageService.store(storageKey, content);
            }
            return toResponse(existing);
        }

        StaticAsset asset;
        if (existing == null) {
            asset = new StaticAsset();
            asset.setStorageKey(storageKey);
            asset.setFileHash(hash);
            asset.setKind(StaticAsset.AssetKind.IMAGE);
        } else {
            // 曾逻辑删除的同内容资源：复用记录并重新激活
            asset = existing;
        }
        asset.setUrl(url);
        asset.setOriginalName(originalName);
        asset.setMimeType(expectedMime);
        asset.setFileSize((long) content.length);
        asset.setStatus(StaticAsset.AssetStatus.ACTIVE);

        storageService.store(storageKey, content);
        StaticAsset saved = staticAssetRepository.saveAndFlush(asset);
        entityManager.refresh(saved);
        log.info("静态资源已登记 id={} storageKey={} size={}B", saved.getId(), storageKey, content.length);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> list(AssetQuery query) {
        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        StaticAsset.AssetKind kind = StringUtils.hasText(query.getKind())
                ? StaticAsset.AssetKind.valueOf(query.getKind())
                : null;
        StaticAsset.AssetStatus status = StringUtils.hasText(query.getStatus())
                ? StaticAsset.AssetStatus.valueOf(query.getStatus())
                : null;

        Page<StaticAsset> page;
        if (kind != null && status != null) {
            page = staticAssetRepository.findByKindAndStatus(kind, status, pageable);
        } else if (kind != null) {
            page = staticAssetRepository.findByKind(kind, pageable);
        } else if (status != null) {
            page = staticAssetRepository.findByStatus(status, pageable);
        } else {
            page = staticAssetRepository.findAll(pageable);
        }
        return PageResponse.of(page, this::toResponse);
    }

    /**
     * 删除资源。
     *
     * @param hard {@code false}（默认）逻辑删除：保留记录与文件，可恢复；
     *             {@code true} 物理删除：同时删除数据库记录与磁盘文件，不可恢复
     */
    @Transactional
    public void delete(Long id, boolean hard) {
        StaticAsset asset = staticAssetRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
        if (hard) {
            staticAssetRepository.delete(asset);
            staticAssetRepository.flush();
            storageService.delete(asset.getStorageKey());
            log.info("物理删除静态资源 id={} storageKey={}", id, asset.getStorageKey());
            return;
        }
        if (asset.getStatus() == StaticAsset.AssetStatus.DELETED) {
            return;
        }
        asset.setStatus(StaticAsset.AssetStatus.DELETED);
        staticAssetRepository.saveAndFlush(asset);
        log.info("逻辑删除静态资源 id={} storageKey={}", id, asset.getStorageKey());
    }

    private String buildStorageKey(String hash, String extension) {
        return "image/" + hash.substring(0, 2) + "/" + hash + "." + extension;
    }

    private AssetResponse toResponse(StaticAsset asset) {
        return new AssetResponse(
                asset.getId(),
                asset.getUrl(),
                asset.getOriginalName(),
                asset.getMimeType(),
                asset.getFileSize() == null ? 0L : asset.getFileSize(),
                asset.getFileHash(),
                asset.getKind() == null ? null : asset.getKind().name(),
                asset.getStatus() == null ? null : asset.getStatus().name(),
                asset.getCreatedAt());
    }

    private BusinessException tooLarge(long maxBytes) {
        return new BusinessException(ErrorCode.ASSET_TOO_LARGE,
                "文件大小不能超过 " + formatBytes(maxBytes));
    }

    private BusinessException notAllowed(String detail) {
        return new BusinessException(ErrorCode.ASSET_TYPE_NOT_ALLOWED,
                ErrorCode.ASSET_TYPE_NOT_ALLOWED.message() + "：" + detail
                        + "（允许 " + String.join(", ", allowedTypes) + "）");
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return (bytes / (1024L * 1024L)) + "MB";
        }
        if (bytes >= 1024L) {
            return (bytes / 1024L) + "KB";
        }
        return bytes + "B";
    }

    private String sanitizeOriginalName(String originalName) {
        String candidate = StringUtils.hasText(originalName)
                ? StringUtils.cleanPath(originalName.trim())
                : "upload";
        int separator = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
        if (separator >= 0) {
            candidate = candidate.substring(separator + 1);
        }
        candidate = candidate.replaceAll("[\\p{Cntrl}]", "").trim();
        if (candidate.isEmpty() || ".".equals(candidate) || "..".equals(candidate)) {
            candidate = "upload";
        }
        return candidate.length() > 255 ? candidate.substring(0, 255) : candidate;
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeMimeType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return null;
        }
        String value = contentType.trim().toLowerCase(Locale.ROOT);
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon).trim();
        }
        return "image/jpg".equals(value) ? "image/jpeg" : value;
    }

    /** 只做文件头魔数识别，不真正解码图片，避免解码器漏洞与额外开销。 */
    private String sniffImageMime(byte[] data) {
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && (data[4] & 0xFF) == 0x0D && (data[5] & 0xFF) == 0x0A
                && (data[6] & 0xFF) == 0x1A && (data[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8'
                && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return "image/gif";
        }
        if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
