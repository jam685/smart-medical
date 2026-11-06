package com.hospital.smartmedical.module.admin.scheduler;

import com.hospital.smartmedical.module.admin.entity.DataQuality;
import com.hospital.smartmedical.module.admin.mapper.DataQualityMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DataQualityScheduler 单元测试。
 * <p>
 * 覆盖场景：
 * <ul>
 *   <li>正常全量巡检（所有表有数据）</li>
 *   <li>所有表为空（边界 0）</li>
 *   <li>Mapper 返回 null 自动兜底为 0</li>
 *   <li>巡检中途抛异常（不向上传播，insert 不被调用）</li>
 * </ul>
 * 不依赖 Spring 容器、不连 DB、不跑定时触发——直接调 runQualityCheck() 公共入口。
 */
@ExtendWith(MockitoExtension.class)
class DataQualitySchedulerTest {

    @Mock
    DataQualityMapper dqMapper;

    @InjectMocks
    DataQualityScheduler scheduler;

    // ===================== 正常场景 =====================

    @Test
    @DisplayName("正常巡检：6张表共243行，缺失率1.2%，异常率0.8%")
    void runQualityCheck_fullSnapshot() {
        // 6 张表 countTable 分别返回 8/19/24/95/23/73（合计 242，实际 243 调多次）
        int[] counts = {8, 19, 24, 95, 23, 73};
        AtomicInteger idx = new AtomicInteger(0);
        when(dqMapper.countTable(anyString())).thenAnswer(inv -> counts[idx.getAndIncrement() % counts.length]);

        // labeled = 12 + 50 + 6 = 68
        when(dqMapper.countKnowledgeParsed()).thenReturn(12);
        when(dqMapper.countDoctorWithSpecialty()).thenReturn(50);
        when(dqMapper.countPatientWithPhone()).thenReturn(6);

        // missingRate = 15 / 1250 = 0.0120
        when(dqMapper.totalFieldChecks()).thenReturn(1250);
        when(dqMapper.totalMissingFields()).thenReturn(15);

        // abnormalRate = 2 / 243 = 0.0082
        when(dqMapper.totalRowsInCore()).thenReturn(243);
        when(dqMapper.totalAbnormalRecords()).thenReturn(2);

        // 执行
        scheduler.runQualityCheck();

        // 验证 insert 被调了一次，且快照字段正确
        ArgumentCaptor<DataQuality> captor = ArgumentCaptor.forClass(DataQuality.class);
        verify(dqMapper, times(1)).insert(captor.capture());

        DataQuality snap = captor.getValue();
        assertThat(snap.getDataCount()).isEqualTo(242); // 8+19+24+95+23+73
        assertThat(snap.getLabeledCount()).isEqualTo(68);
        assertThat(snap.getMissingRate())
                .isEqualByComparingTo(BigDecimal.valueOf(15).divide(BigDecimal.valueOf(1250), 4, RoundingMode.HALF_UP));
        assertThat(snap.getAbnormalRate())
                .isEqualByComparingTo(BigDecimal.valueOf(2).divide(BigDecimal.valueOf(243), 4, RoundingMode.HALF_UP));
    }

    // ===================== 空表边界 =====================

    @Test
    @DisplayName("所有表为空：dataCount=0，两个率都是ZERO")
    void runQualityCheck_emptyTables() {
        when(dqMapper.countTable(anyString())).thenReturn(0);
        when(dqMapper.countKnowledgeParsed()).thenReturn(0);
        when(dqMapper.countDoctorWithSpecialty()).thenReturn(0);
        when(dqMapper.countPatientWithPhone()).thenReturn(0);
        when(dqMapper.totalFieldChecks()).thenReturn(0);   // 分母为 0 → missingRate=ZERO
        when(dqMapper.totalMissingFields()).thenReturn(0);
        when(dqMapper.totalRowsInCore()).thenReturn(0);   // 分母为 0 → abnormalRate=ZERO
        when(dqMapper.totalAbnormalRecords()).thenReturn(0);

        scheduler.runQualityCheck();

        ArgumentCaptor<DataQuality> captor = ArgumentCaptor.forClass(DataQuality.class);
        verify(dqMapper).insert(captor.capture());

        DataQuality snap = captor.getValue();
        assertThat(snap.getDataCount()).isEqualTo(0);
        assertThat(snap.getLabeledCount()).isEqualTo(0);
        assertThat(snap.getMissingRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snap.getAbnormalRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===================== null 兜底 =====================

    @Test
    @DisplayName("Mapper 返回 null：nvl 兜底为 0，不 NPE")
    void runQualityCheck_nullFallback() {
        // countTable 返回 null（某个表查不到）→ 当作 0 处理
        when(dqMapper.countTable(anyString())).thenReturn(null);
        // labeled 的三个方法也返回 null
        when(dqMapper.countKnowledgeParsed()).thenReturn(null);
        when(dqMapper.countDoctorWithSpecialty()).thenReturn(null);
        when(dqMapper.countPatientWithPhone()).thenReturn(null);
        // 缺失率/异常率分母为 null → 返回 ZERO
        when(dqMapper.totalFieldChecks()).thenReturn(null);
        when(dqMapper.totalMissingFields()).thenReturn(null);
        when(dqMapper.totalRowsInCore()).thenReturn(null);
        when(dqMapper.totalAbnormalRecords()).thenReturn(null);

        // 关键：不抛异常，且 insert 被调用（dataCount=0, labeledCount=0, 两个率=0）
        scheduler.runQualityCheck();

        ArgumentCaptor<DataQuality> captor = ArgumentCaptor.forClass(DataQuality.class);
        verify(dqMapper).insert(captor.capture());

        DataQuality snap = captor.getValue();
        assertThat(snap.getDataCount()).isEqualTo(0);
        assertThat(snap.getLabeledCount()).isEqualTo(0);
        assertThat(snap.getMissingRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(snap.getAbnormalRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===================== 异常隔离 =====================

    @Test
    @DisplayName("巡检中途 SQL 异常：不向上传播，insert 不被调用")
    void runQualityCheck_exceptionIsSwallowed() {
        // 第一个表就炸了
        when(dqMapper.countTable(anyString())).thenThrow(new RuntimeException("connection refused"));

        // 执行不抛异常
        scheduler.runQualityCheck();

        // insert 不被调用——因为在 catch 里了
        verify(dqMapper, never()).insert(any(DataQuality.class));
    }

    @Test
    @DisplayName("labeledCount 计算中途异常：insert 不被调用")
    void runQualityCheck_exceptionDuringLabeled() {
        when(dqMapper.countTable(anyString())).thenReturn(1);
        // labeled 部分炸了
        when(dqMapper.countKnowledgeParsed()).thenThrow(new RuntimeException("boom"));

        scheduler.runQualityCheck();

        verify(dqMapper, never()).insert(any(DataQuality.class));
    }

    // ===================== 比例边界 =====================

    @Test
    @DisplayName("缺失率 50%：100 项检查有 50 项缺失")
    void runQualityCheck_halfMissing() {
        when(dqMapper.countTable(anyString())).thenReturn(1);
        when(dqMapper.countKnowledgeParsed()).thenReturn(0);
        when(dqMapper.countDoctorWithSpecialty()).thenReturn(0);
        when(dqMapper.countPatientWithPhone()).thenReturn(0);
        when(dqMapper.totalFieldChecks()).thenReturn(100);
        when(dqMapper.totalMissingFields()).thenReturn(50);  // 50%
        when(dqMapper.totalRowsInCore()).thenReturn(6);
        when(dqMapper.totalAbnormalRecords()).thenReturn(0);

        scheduler.runQualityCheck();

        ArgumentCaptor<DataQuality> captor = ArgumentCaptor.forClass(DataQuality.class);
        verify(dqMapper).insert(captor.capture());

        assertThat(captor.getValue().getMissingRate())
                .isEqualByComparingTo(new BigDecimal("0.5000"));
    }
}
