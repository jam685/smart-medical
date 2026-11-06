package com.hospital.smartmedical.common.storage;

import com.hospital.smartmedical.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 上传文件命名与校验测试。
 *
 * <p>回归点：旧命名为「业务前缀 + 毫秒时间戳」（如 report_1789...jpg），
 * 配合公开的 /uploads/** 可被枚举；现在改为随机 UUID 且校验 Content-Type 与子目录。</p>
 */
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        ReflectionTestUtils.setField(service, "contextPath", "/api/smart-medical/v1");
    }

    @Test
    @DisplayName("文件名使用 32 位随机十六进制，不含业务前缀与时间戳")
    void usesRandomFileName() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report_1789807390932.jpg", "image/jpeg", new byte[]{1, 2, 3});

        String url = service.saveImage(file, "reports");

        assertTrue(url.matches("/api/smart-medical/v1/uploads/reports/[0-9a-f]{32}\\.jpg"),
                "实际 URL：" + url);
        assertFalse(url.contains("report_"), "不得包含原文件名/业务前缀");
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        assertTrue(Files.exists(tempDir.resolve("reports").resolve(fileName)), "文件应真实落盘");
    }

    @Test
    @DisplayName("非图片 Content-Type 直接拒绝")
    void rejectsNonImageContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.jpg", "text/plain", new byte[]{1, 2, 3});
        assertThrows(BizException.class, () -> service.saveImage(file, "reports"));
    }

    @Test
    @DisplayName("未知子目录拒绝（防路径穿越）")
    void rejectsUnknownSubDir() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[]{1});
        assertThrows(BizException.class, () -> service.saveImage(file, "../etc"));
    }

    @Test
    @DisplayName("扩展名白名单之外拒绝")
    void rejectsDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.svg", "image/svg+xml", new byte[]{1});
        assertThrows(BizException.class, () -> service.saveImage(file, "chat"));
    }

    @Test
    @DisplayName("空文件拒绝")
    void rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThrows(BizException.class, () -> service.saveAvatar(file));
        assertEquals(0, tempDir.toFile().listFiles() == null ? 0 : tempDir.toFile().listFiles().length);
    }
}
