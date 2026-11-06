package com.hospital.smartmedical.module.admin.scheduler;

import com.hospital.smartmedical.module.admin.entity.DataQuality;
import com.hospital.smartmedical.module.admin.mapper.DataQualityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

/**
 * 数据质量定时巡检任务。
 * <p>
 * 每天凌晨 02:00 扫描 6 张核心业务表，统计：
 * <ul>
 *   <li>总数据量（dataCount）</li>
 *   <li>已标注/已处理量（labeledCount）—— 知识库已解析 + 档案信息完整</li>
 *   <li>关键字段缺失率（missingRate）</li>
 *   <li>值异常率（abnormalRate）—— 年龄越界、手机号格式错误、状态枚举非法等</li>
 * </ul>
 * 巡检结果 INSERT 进 sm_data_quality 作为历史快照，admin 管理页数据质量卡片读取展示。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataQualityScheduler {

    private final DataQualityMapper dqMapper;

    /** 核心巡检表列表（按巡检 SQL 顺序） */
    private static final List<String> CORE_TABLES = Arrays.asList(
            "sm_patient",
            "sm_doctor",
            "sm_online_consult_record",
            "sm_consult_message",
            "sm_knowledge_document",
            "sm_knowledge_chunk"
    );

    /**
     * 启动后 15s 先跑一次（便于本地验证），之后每天凌晨 02:00 跑一次。
     */
    @Scheduled(initialDelay = 15_000L, fixedDelay = 24 * 60 * 60 * 1000L)
    public void runQualityCheck() {
        log.info("========== [DataQualityScheduler] 开始数据质量巡检 ==========");
        long t0 = System.currentTimeMillis();

        try {
            // 1) 总数据量：6 张核心表行数之和
            int dataCount = sumTableRows();

            // 2) 已标注/已处理量
            int labeledCount = computeLabeledCount();

            // 3) 关键字段缺失率
            BigDecimal missingRate = computeMissingRate();

            // 4) 数据异常率
            BigDecimal abnormalRate = computeAbnormalRate();

            // 5) 写快照
            DataQuality snap = new DataQuality();
            snap.setDataCount(dataCount);
            snap.setLabeledCount(labeledCount);
            snap.setMissingRate(missingRate);
            snap.setAbnormalRate(abnormalRate);
            dqMapper.insert(snap);

            long cost = System.currentTimeMillis() - t0;
            log.info("========== [DataQualityScheduler] 巡检完成 ({}ms) ==========", cost);
            log.info("  dataCount={}, labeledCount={}, missingRate={}%, abnormalRate={}%",
                    dataCount, labeledCount,
                    missingRate == null ? "N/A" : missingRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP),
                    abnormalRate == null ? "N/A" : abnormalRate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
        } catch (Exception e) {
            log.error("[DataQualityScheduler] 巡检失败", e);
        }
    }

    // =========================== 巡检逻辑 ===========================

    /** 6 张核心表行数求和。 */
    private int sumTableRows() {
        int total = 0;
        for (String t : CORE_TABLES) {
            Integer cnt = dqMapper.countTable(t);
            if (cnt != null) total += cnt;
        }
        return total;
    }

    /**
     * 已标注/已处理量：
     * <ul>
     *   <li>sm_knowledge_document 中 parse_status=PARSED 且 chunk_count>0</li>
     *   <li>sm_doctor 中 specialty 非空（档案完整）</li>
     *   <li>sm_patient 中 phone 非空（档案完整）</li>
     * </ul>
     */
    private int computeLabeledCount() {
        int knowledgeParsed = nvl(dqMapper.countKnowledgeParsed());
        int doctorFull = nvl(dqMapper.countDoctorWithSpecialty());
        int patientFull = nvl(dqMapper.countPatientWithPhone());
        return knowledgeParsed + doctorFull + patientFull;
    }

    /**
     * 关键字段缺失率 = 缺失总次数 / 总检查次数。
     * 检查项：
     * <ul>
     *   <li>sm_patient: name/phone</li>
     *   <li>sm_doctor: real_name/department</li>
     *   <li>sm_online_consult_record: patient_id/doctor_id/channel</li>
     *   <li>sm_knowledge_document: document_name/parse_status</li>
     * </ul>
     */
    private BigDecimal computeMissingRate() {
        Integer checked = dqMapper.totalFieldChecks();
        Integer missing = dqMapper.totalMissingFields();
        if (checked == null || missing == null || checked == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(missing)
                .divide(BigDecimal.valueOf(checked), 4, RoundingMode.HALF_UP);
    }

    /**
     * 数据异常率 = 异常记录数 / 核心表总记录数。
     * 检查项：
     * <ul>
     *   <li>sm_patient: age 越界 (<0 或 >120)</li>
     *   <li>sm_doctor: phone 不是 11 位数字</li>
     *   <li>sm_knowledge_document: parse_status 不在合法枚举内</li>
     * </ul>
     */
    private BigDecimal computeAbnormalRate() {
        Integer total = dqMapper.totalRowsInCore();
        Integer abnormal = dqMapper.totalAbnormalRecords();
        if (total == null || abnormal == null || total == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(abnormal)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    private static int nvl(Integer v) {
        return v == null ? 0 : v;
    }
}
