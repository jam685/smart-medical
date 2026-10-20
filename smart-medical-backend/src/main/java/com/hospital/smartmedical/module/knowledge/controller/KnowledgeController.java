package com.hospital.smartmedical.module.knowledge.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeChunk;
import com.hospital.smartmedical.module.knowledge.entity.KnowledgeDocument;
import com.hospital.smartmedical.module.knowledge.entity.MedicalKnowledgeBase;
import com.hospital.smartmedical.module.knowledge.entity.VectorIndex;
import com.hospital.smartmedical.module.knowledge.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 医学知识库控制器（/admin/knowledge）。
 *
 * <p>管理员维护医学知识库体系：知识库、文档、片段与向量索引（KB-01~06）。
 * 该前缀命中 AuthInterceptor 的 /admin/ 管理员角色校验。</p>
 */
@RestController
@RequestMapping("/admin/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /** 知识库列表（KB-01）。 */
    @GetMapping("/bases")
    public Result<List<MedicalKnowledgeBase>> listBases() {
        return Result.ok(knowledgeService.listBases());
    }

    /** 新增医学知识库（KB-02）。 */
    @PostMapping("/bases")
    public Result<MedicalKnowledgeBase> createBase(@RequestBody MedicalKnowledgeBase base) {
        return Result.ok(knowledgeService.createBase(base));
    }

    /** 知识文档列表（KB-03）。 */
    @GetMapping("/documents")
    public Result<List<KnowledgeDocument>> listDocuments(
            @RequestParam(required = false) Long knowledgeBaseId) {
        return Result.ok(knowledgeService.listDocuments(knowledgeBaseId));
    }

    /** 新增知识文档（KB-04）。 */
    @PostMapping("/documents")
    public Result<KnowledgeDocument> createDocument(@RequestBody KnowledgeDocument document) {
        return Result.ok(knowledgeService.createDocument(document));
    }

    /** 知识片段列表（KB-05）。 */
    @GetMapping("/chunks")
    public Result<List<KnowledgeChunk>> listChunks(@RequestParam(required = false) Long documentId) {
        return Result.ok(knowledgeService.listChunks(documentId));
    }

    /** 向量化检索记录列表（KB-06）。 */
    @GetMapping("/vector-indexes")
    public Result<List<VectorIndex>> listVectorIndexes(
            @RequestParam(required = false) Long sourceDocumentId) {
        return Result.ok(knowledgeService.listVectorIndexes(sourceDocumentId));
    }
}