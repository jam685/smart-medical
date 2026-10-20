package com.hospital.smartmedical.module.prompt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.prompt.entity.PromptTemplate;
import com.hospital.smartmedical.module.prompt.mapper.PromptTemplateMapper;
import com.hospital.smartmedical.module.prompt.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 提示词模板服务实现（/admin/prompt-templates，PT-01~04）。
 */
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    /** 模板列表（PT-01）。 */
    @Override
    public List<PromptTemplate> list() {
        return promptTemplateMapper.selectList(
                new LambdaQueryWrapper<PromptTemplate>().orderByDesc(PromptTemplate::getId));
    }

    /** 新增模板（PT-02）。 */
    @Override
    public PromptTemplate create(PromptTemplate template) {
        if (template.getTemplateCode() == null || template.getTemplateCode().isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        if (template.getEnabled() == null) {
            template.setEnabled(1);
        }
        promptTemplateMapper.insert(template);
        return template;
    }

    /** 修改模板（PT-03）。 */
    @Override
    public PromptTemplate update(Long id, PromptTemplate template) {
        PromptTemplate exist = promptTemplateMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        exist.setTemplateName(template.getTemplateName());
        exist.setScene(template.getScene());
        exist.setVariables(template.getVariables());
        exist.setPromptContent(template.getPromptContent());
        exist.setEnabled(template.getEnabled());
        exist.setUpdatedAt(LocalDateTime.now());
        if (template.getTemplateCode() != null && !template.getTemplateCode().isBlank()) {
            exist.setTemplateCode(template.getTemplateCode());
        }
        promptTemplateMapper.updateById(exist);
        return exist;
    }

    /** 删除模板（PT-04）。 */
    @Override
    public void delete(Long id) {
        PromptTemplate exist = promptTemplateMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        promptTemplateMapper.deleteById(id);
    }
}