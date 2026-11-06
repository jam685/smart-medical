package com.hospital.smartmedical.module.ai.agent.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 中文时间解析测试。
 *
 * <p>回归点：旧实现只认阿拉伯数字（{@code \d{1,2}[点时]}），
 * "下午四点""十点半"解析失败 → 取最近号源 → 自动挂错时间。</p>
 */
class TimeTextParserTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Test
    @DisplayName("中文数字小时：四 / 十 / 十一 / 二十三 / 两")
    void chineseHourNumbers() {
        assertEquals(LocalDateTime.of(2026, 11, 6, 16, 0), parse("下午四点"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 10, 0), parse("上午十点"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 11, 0), parse("十一点"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 23, 0), parse("晚上十一点"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 14, 0), parse("下午两点"));
    }

    @Test
    @DisplayName("中文分钟：半 / 一刻 / 三刻 / 二十")
    void chineseMinutes() {
        assertEquals(LocalDateTime.of(2026, 11, 6, 16, 30), parse("下午四点半"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 10, 15), parse("上午十点一刻"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 10, 45), parse("上午十点三刻"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 20), parse("上午九点二十分"));
    }

    @Test
    @DisplayName("阿拉伯数字与冒号格式仍兼容")
    void arabicAndColon() {
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 30), parse("9:30"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 14, 5), parse("下午2:05"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 16, 30), parse("下午4点半"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 9, 0), parse("上午9点"));
    }

    @Test
    @DisplayName("日期词：明天 / 后天 / 大后天（大后天优先）")
    void dateWords() {
        assertEquals(LocalDateTime.of(2026, 11, 7, 9, 0), parse("明天上午九点"));
        assertEquals(LocalDateTime.of(2026, 11, 8, 9, 0), parse("后天上午九点"));
        assertEquals(LocalDateTime.of(2026, 11, 9, 9, 0), parse("大后天上午九点"));
    }

    @Test
    @DisplayName("时段换算：凌晨12点=0点、中午1点=13点、晚上12点=0点")
    void meridiemConversion() {
        assertEquals(LocalDateTime.of(2026, 11, 6, 0, 0), parse("凌晨十二点"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 13, 0), parse("中午一点"));
        assertEquals(LocalDateTime.of(2026, 11, 6, 0, 0), parse("晚上十二点"));
    }

    @Test
    @DisplayName("无法解析时返回 null（不猜时间）")
    void unparseableReturnsNull() {
        assertNull(parse("挂号流程是什么"));
        assertNull(parse("明天上午"));
        assertNull(parse(""));
        assertNull(parse(null));
    }

    @Test
    @DisplayName("中文数字解析边界：十=10、十二=12、二十=20、二十三=23")
    void numberBoundaries() {
        assertEquals(10, TimeTextParser.parseNumber("十"));
        assertEquals(12, TimeTextParser.parseNumber("十二"));
        assertEquals(20, TimeTextParser.parseNumber("二十"));
        assertEquals(23, TimeTextParser.parseNumber("二十三"));
        assertEquals(2, TimeTextParser.parseNumber("两"));
    }

    private static LocalDateTime parse(String text) {
        return TimeTextParser.parse(text, TODAY);
    }
}
