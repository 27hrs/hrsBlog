package com.example.hrsblog.controller.admin;

import com.example.hrsblog.common.ApiResponse;
import com.example.hrsblog.common.PageResponse;
import com.example.hrsblog.dto.AssetQuery;
import com.example.hrsblog.dto.AssetResponse;
import com.example.hrsblog.service.StaticAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员静态资源接口：上传登记 / 列表 / 删除，均需要 ROLE_ADMIN。
 *
 * <p>文件本体由存储层保存，数据库只记录链接与元数据；对外的读取入口是 {@code /assets/**}。
 */
@RestController
@RequestMapping("/api/admin/assets")
@RequiredArgsConstructor
public class AdminAssetController {

    private final StaticAssetService staticAssetService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AssetResponse>> upload(@RequestPart("file") MultipartFile file) {
        AssetResponse created = staticAssetService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @GetMapping
    public ApiResponse<PageResponse<AssetResponse>> list(@Valid @ModelAttribute AssetQuery query) {
        return ApiResponse.ok(staticAssetService.list(query));
    }

    /**
     * 删除资源。
     *
     * @param hard {@code false} 逻辑删除（默认），{@code true} 同时删除记录与文件
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id,
                                    @RequestParam(name = "hard", defaultValue = "false") boolean hard) {
        staticAssetService.delete(id, hard);
        return ApiResponse.ok();
    }
}
