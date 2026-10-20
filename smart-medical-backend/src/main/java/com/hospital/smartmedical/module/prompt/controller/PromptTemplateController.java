package com.hospital.smartmedical.module.prompt.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.prompt.entity.PromptTemplate;
import com.hospital.smartmedical.module.prompt.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 提示词模板控制器（/admin/prompt-templates）。
 *
 * <p>管理员维护 AI 提示词模板（PT-01~04）。该前缀命中 AuthInterceptor 的 /admin/ 管理员角色校验。</p>
 */
@RestController
@RequestMapping("/admin/prompt-templates")
@RequiredArgsConstructor
public class PromptTemplateController {

    private final PromptTemplateService promptTemplateService;

    /** 提示词模板列表（PT-01）。 */
    @GetMapping
    public Result<List<PromptTemplate>> list() {
        return Result.ok(promptTemplateService.list());
    }

    /** 新增提示词模板（PT-02）。 */
    @PostMapping
    public Result<PromptTemplate> create(@RequestBody PromptTemplate template) {
        return Result.ok(promptTemplateService.create(template));
    }

    /** 修改提示词模板（PT-03）。 */
    @PutMapping("/{id}")
    public Result<PromptTemplate> update(@PathVariable Long id, @RequestBody PromptTemplate template) {
        return Result.ok(promptTemplateService.update(id, template));
    }

    /** 删除提示词模板（PT-04）。 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        promptTemplateService.delete(id);
        return Result.ok();
    }
}