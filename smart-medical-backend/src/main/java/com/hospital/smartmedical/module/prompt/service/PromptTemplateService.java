package com.hospital.smartmedical.module.prompt.service;

import com.hospital.smartmedical.module.prompt.entity.PromptTemplate;

import java.util.List;

/**
 * 提示词模板服务接口（/admin/prompt-templates）。
 *
 * <p>管理端对 AI 提示词模板做增删改查（PT-01~04）。</p>
 */
public interface PromptTemplateService {

    /**
     * 提示词模板列表（PT-01）。
     */
    List<PromptTemplate> list();

    /**
     * 新增提示词模板（PT-02）。
     */
    PromptTemplate create(PromptTemplate template);

    /**
     * 修改提示词模板（PT-03）。
     */
    PromptTemplate update(Long id, PromptTemplate template);

    /**
     * 删除提示词模板（PT-04）。
     */
    void delete(Long id);
}