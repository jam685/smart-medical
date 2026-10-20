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

/**
 * 本地文件存储（Demo 环境）：头像/报告/聊天图片等用户上传文件统一存 {upload-dir}/{子目录}/ 目录。
 * 返回含 context-path 的完整相对路径，前端可直接作为 img src 使用。
 */
@Component
public class FileStorageService {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /**
     * 保存头像文件
     *
     * @param file   图片文件
     * @param prefix 文件名前缀（如 patient_1 / doctor_2），避免重名
     * @return 头像可访问路径（含 context-path）
     */
    public String saveAvatar(MultipartFile file, String prefix) {
        return saveImage(file, "avatars", prefix);
    }

    /**
     * 保存任意图片文件到指定子目录
     *
     * @param file   图片文件
     * @param subDir 子目录（如 reports / chat）
     * @param prefix 文件名前缀，避免重名
     * @return 图片可访问路径（含 context-path）
     */
    public String saveImage(MultipartFile file, String subDir, String prefix) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的图片文件");
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
        String fileName = prefix + "_" + System.currentTimeMillis() + "." + ext;
        try {
            file.transferTo(dir.resolve(fileName).toFile());
        } catch (IOException e) {
            throw new BizException("图片保存失败，请重试");
        }
        return contextPath + "/uploads/" + subDir + "/" + fileName;
    }
}
