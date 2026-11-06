package com.hospital.smartmedical.module.ai.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.ai.agent.cache.SemanticCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 语义缓存管理接口（/admin/semantic-cache，管理员专属）。
 *
 * <p>P1 影子模式：提供命中统计（观察命中率与抽检）与一键清空。
 * 该前缀命中 AuthInterceptor 的 /admin/ 管理员角色校验。</p>
 */
@RestController
@RequestMapping("/admin/semantic-cache")
@RequiredArgsConstructor
public class SemanticCacheController {

    private final SemanticCacheService semanticCacheService;

    /**
     * 语义缓存运行统计：开关、阈值、参与数、命中数、命中率、写入数、跳过数。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.ok(semanticCacheService.stats());
    }

    /**
     * 清空缓存向量与统计（重建内存库 / 清空 Milvus 集合）。
     */
    @DeleteMapping
    public Result<String> clear() {
        semanticCacheService.clear();
        return Result.ok("语义缓存已清空");
    }

    /**
     * 手动触发孤儿向量清理（运维用）：删除「答案已按 TTL 过期、向量仍留在库中」的条目。
     *
     * <p>定时任务每小时 :07 自动执行一次；此接口用于排查或立即回收空间。</p>
     *
     * @return 本次清理条数
     */
    @PostMapping("/clean")
    public Result<Map<String, Object>> clean() {
        int cleaned = semanticCacheService.cleanOrphanVectors();
        return Result.ok(Map.of("cleaned", cleaned));
    }
}
