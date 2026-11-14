package com.example.hrsblog.service;

import com.example.hrsblog.common.BusinessException;
import com.example.hrsblog.common.ErrorCode;
import com.example.hrsblog.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘存储实现。
 *
 * <p>安全要点：
 * <ul>
 *   <li>把 storageKey 解析成绝对路径后必须仍位于存储根目录之内，防止 {@code ../} 路径穿越；</li>
 *   <li>先写临时文件再原子移动，避免并发的半截文件被访问到，也避免覆盖失败留下损坏文件。</li>
 * </ul>
 */
@Slf4j
@Service
public class LocalStorageService implements StaticStorageService {

    private static final String URL_PREFIX = "/assets/";

    private final Path root;
    private final String publicBaseUrl;

    public LocalStorageService(StorageProperties properties) {
        this.root = Path.of(properties.localDir()).toAbsolutePath().normalize();
        String base = properties.publicBaseUrl() == null ? "" : properties.publicBaseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.publicBaseUrl = base;
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建静态资源存储目录: " + root, ex);
        }
        log.info("静态资源存储根目录: {}，对外链接前缀: {}", root, publicBaseUrl.isEmpty() ? "(站内相对路径)" : publicBaseUrl);
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            Files.write(temp, content);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "写入静态资源文件失败", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolve(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "删除静态资源文件失败", ex);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    @Override
    public String urlOf(String storageKey) {
        return publicBaseUrl + URL_PREFIX + storageKey;
    }

    private Path resolve(String storageKey) {
        if (!StringUtils.hasText(storageKey)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "storageKey 不能为空");
        }
        Path candidate = root.resolve(storageKey).normalize();
        if (!candidate.startsWith(root) || candidate.equals(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的存储路径");
        }
        return candidate;
    }
}
