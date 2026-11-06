package com.hospital.smartmedical.common.storage;

import com.hospital.smartmedical.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储：头像/报告/聊天图片等用户上传文件统一存 {upload-dir}/{子目录}/ 目录。
 *
 * <p><b>文件名策略：随机 UUID，不再使用"业务前缀 + 时间戳"。</b>
 * 旧命名（如 {@code report_1789...jpg}）可被枚举：时间戳精度到毫秒、前缀可猜，
 * 再加上 /uploads/** 是公开静态资源，等于把报告图片暴露给任何拿到 URL 的人。
 * 现在文件名不可预测，URL 退化为"能力令牌"（capability URL）。</p>
 *
 * <p>同时做两层校验：扩展名白名单 + Content-Type 必须以 image/ 开头，降低上传非图片文件的风险。</p>
 *
 * <p>如需更强的访问控制，应改为"鉴权下载接口 + 短期签名 URL"，而不是公开静态目录。</p>
 */
@Component
public class FileStorageService {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 子目录名白名单（防止路径穿越） */
    private static final Set<String> ALLOWED_SUB_DIRS = Set.of("avatars", "reports", "chat");

    /**
     * 保存头像文件。
     *
     * @param file 图片文件
     * @return 头像可访问路径（含 context-path）
     */
    public String saveAvatar(MultipartFile file) {
        return saveImage(file, "avatars");
    }

    /**
     * 保存任意图片文件到指定子目录。
     *
     * @param file   图片文件
     * @param subDir 子目录（avatars / reports / chat）
     * @return 图片可访问路径（含 context-path）
     */
    public String saveImage(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片文件");
        }
        if (subDir == null || !ALLOWED_SUB_DIRS.contains(subDir)) {
            throw new BizException("不支持的上传目录");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BizException("仅支持上传图片文件");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.contains(".")) {
            throw new BizException("图片文件格式不正确");
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException("仅支持 jpg / jpeg / png / webp / gif 格式的图片");
        }

        // 必须用绝对路径：MultipartFile.transferTo 传相对路径时会解析到容器临时目录导致 IO 失败
        Path dir = Paths.get(uploadDir, subDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BizException("图片存储目录创建失败");
        }
        // 随机文件名：不可枚举、不泄露业务 ID 与上传时间
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        try {
            file.transferTo(dir.resolve(fileName).toFile());
        } catch (IOException e) {
            throw new BizException("图片保存失败，请重试");
        }
        return contextPath + "/uploads/" + subDir + "/" + fileName;
    }
}
