package com.hospital.smartmedical.module.ai.agent.llm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.module.prompt.entity.PromptTemplate;
import com.hospital.smartmedical.module.prompt.mapper.PromptTemplateMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词加载器（Agent 提示词统一从管理员端 sm_prompt_template 表读取，支持热更新）。
 *
 * <p>加载优先级：1) 数据库模板表（管理员端维护，每次实时查询，改库即生效，无需重启）；
 * 2) classpath:ai-prompts.yaml 兜底（未在库中配置时的默认）；
 * 3) 均无则记录缺失日志并返回空串（提示管理员补齐模板）。
 * 启动时自动将内置 Agent 模板（patient-dialog / doctor-assist / business）初始化入库，
 * 保证管理员端【提示词模板】列表有对应项可直接编辑。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateLoader {

    /** 内置 Agent 模板编码 → [场景, 名称]（编码与 ai-prompts.yaml 顶层 key 一致） */
    private static final Map<String, String[]> BUILTIN_TEMPLATES = Map.ofEntries(
            Map.entry("patient-dialog", new String[]{"PATIENT", "患者预问诊 Agent"}),
            Map.entry("doctor-assist", new String[]{"DOCTOR", "医生辅助 Agent"}),
            Map.entry("business", new String[]{"PATIENT", "业务助手 Agent"}),
            Map.entry("business-intent", new String[]{"PATIENT", "业务意图识别 Agent"}),
            Map.entry("business-polish", new String[]{"PATIENT", "业务回复润色 Agent"}),
            Map.entry("assistant-dialog", new String[]{"PATIENT", "数字医生小林对话 Agent"}),
            Map.entry("assistant-intent", new String[]{"PATIENT", "小林意图识别 Agent"}),
            Map.entry("assistant-polish", new String[]{"PATIENT", "小林回复润色 Agent"}),
            Map.entry("doctor-dialog", new String[]{"DOCTOR", "医生端数字医生小林对话 Agent"}),
            Map.entry("doctor-intent", new String[]{"DOCTOR", "医生端小林意图识别 Agent"}),
            Map.entry("doctor-polish", new String[]{"DOCTOR", "医生端小林回复润色 Agent"}));

    /** 占位符正则 {var} */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final PromptTemplateMapper promptTemplateMapper;

    /** yaml 兜底模板缓存（code → 模板文本） */
    private final Map<String, String> yamlTemplates = new HashMap<>();

    /** yaml 是否已加载 */
    private boolean yamlLoaded = false;

    /**
     * 启动时初始化内置 Agent 模板入库（管理员端列表可直接编辑热更新）。
     */
    @PostConstruct
    public void init() {
        Map<String, String> seed = loadYamlTemplates();
        for (Map.Entry<String, String[]> e : BUILTIN_TEMPLATES.entrySet()) {
            String code = e.getKey();
            Long count = promptTemplateMapper.selectCount(
                    new LambdaQueryWrapper<PromptTemplate>()
                            .eq(PromptTemplate::getTemplateCode, code));
            if (count != null && count > 0) {
                continue;
            }
            PromptTemplate t = new PromptTemplate();
            t.setTemplateCode(code);
            t.setTemplateName(e.getValue()[1]);
            t.setScene(e.getValue()[0]);
            t.setVariables(collectVariables(seed.get(code)));
            t.setPromptContent(seed.getOrDefault(code, ""));
            t.setEnabled(1);
            t.setCreatedAt(LocalDateTime.now());
            t.setUpdatedAt(LocalDateTime.now());
            promptTemplateMapper.insert(t);
            log.info("AI 提示词模板初始化入库：{}（{}）", code, t.getTemplateName());
        }
    }

    /**
     * 渲染提示词模板：数据库优先（管理员端热更新），yaml 兜底，均无则告警。
     *
     * @param code      模板编码（对应 sm_prompt_template.template_code 与 yaml 顶层 key）
     * @param variables 注入变量（{key} → value）
     * @return 渲染后的提示词文本
     */
    public String render(String code, Map<String, Object> variables) {
        // 1) 数据库模板表（管理员端热更新，每次实时查询，改库立即生效）
        String dbTemplate = loadFromDb(code);
        if (dbTemplate != null) {
            return fill(dbTemplate, variables);
        }
        // 2) yaml 兜底
        String yamlTemplate = yamlTemplates.get(code);
        if (yamlTemplate == null) {
            yamlTemplates.putAll(loadYamlTemplates());
            yamlTemplate = yamlTemplates.get(code);
        }
        if (yamlTemplate == null) {
            log.warn("提示词模板缺失：{}，请在管理员端【提示词模板】中添加该 Agent 的提示词", code);
            return "";
        }
        return fill(yamlTemplate, variables);
    }

    /**
     * 从数据库读取启用模板内容（template_code + enabled=1）。
     */
    private String loadFromDb(String code) {
        PromptTemplate t = promptTemplateMapper.selectOne(
                new LambdaQueryWrapper<PromptTemplate>()
                        .eq(PromptTemplate::getTemplateCode, code)
                        .eq(PromptTemplate::getEnabled, 1)
                        .last("LIMIT 1"));
        return t == null ? null : t.getPromptContent();
    }

    /**
     * 占位符 {var} 替换为变量值。
     */
    private String fill(String template, Map<String, Object> variables) {
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                String value = e.getValue() == null ? "" : e.getValue().toString();
                result = result.replace("{" + e.getKey() + "}", value);
            }
        }
        return result;
    }

    /**
     * 从模板文本提取占位符变量名列表（逗号拼接），供入库 variables 字段。
     */
    private String collectVariables(String template) {
        if (template == null || template.isBlank()) {
            return null;
        }
        Set<String> vars = new LinkedHashSet<>();
        Matcher m = VAR_PATTERN.matcher(template);
        while (m.find()) {
            vars.add(m.group(1));
        }
        return vars.isEmpty() ? null : String.join(",", vars);
    }

    /**
     * 加载 yaml 兜底模板（code → system 文本，SnakeYAML 解析）。
     */
    private synchronized Map<String, String> loadYamlTemplates() {
        if (yamlLoaded) {
            return new HashMap<>(yamlTemplates);
        }
        try {
            ClassPathResource resource = new ClassPathResource("ai-prompts.yaml");
            if (resource.exists()) {
                try (InputStream in = resource.getInputStream()) {
                    Yaml yaml = new Yaml();
                    Map<String, Object> root = yaml.load(in);
                    if (root != null) {
                        root.forEach((code, v) -> {
                            if (v instanceof Map<?, ?> m) {
                                Object system = m.get("system");
                                yamlTemplates.put(code, system == null ? "" : system.toString());
                            }
                        });
                    }
                }
            }
            yamlLoaded = true;
            log.info("AI 提示词 yaml 兜底模板加载完成，共 {} 个", yamlTemplates.size());
        } catch (Exception e) {
            log.error("AI 提示词 yaml 加载失败：{}", e.getMessage(), e);
            yamlLoaded = true;
        }
        return new HashMap<>(yamlTemplates);
    }
}
