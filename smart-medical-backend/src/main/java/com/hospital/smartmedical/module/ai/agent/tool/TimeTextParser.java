package com.hospital.smartmedical.module.ai.agent.tool;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文时间文本解析器（纯函数、无状态，便于单测）。
 *
 * <p>解决的问题：患者常用「四点」「十点半」「下午两点一刻」这类表达，
 * 旧实现只认阿拉伯数字（{@code \d{1,2}[点时]}），导致"四点"解析失败，
 * 进而触发"没有时间 → 取最近号源 → 自动挂号"的连锁问题。</p>
 *
 * <p>支持范围：
 * <ul>
 *   <li>日期：今天 / 明天 / 明日 / 后天 / 大后天（大后天优先于后天匹配）</li>
 *   <li>时段：上午 / 早上 / 早晨 / 凌晨 / 中午 / 下午 / 晚上</li>
 *   <li>小时：阿拉伯数字（4 / 11）或中文数字（四 / 十 / 十一 / 二十三 / 两）</li>
 *   <li>分钟：半 / 一刻 / 三刻 / 阿拉伯分钟 / 中文分钟，或省略</li>
 *   <li>冒号格式：9:30、21：05</li>
 * </ul></p>
 *
 * <p>实现要点：中文数字采用「十位 + 个位」的简单累加算法——
 * 遇到「十」就把前面累计的数字（缺省为 1）乘 10 加到总数上，
 * 遇到个位数字就先暂存，最后再加。因此 十=10、十二=12、二十=20、二十三=23。</p>
 */
public final class TimeTextParser {

    private TimeTextParser() {
    }

    /** 带「点/时」的时间表达式：可选时段前缀 + 小时 + 可选分钟 */
    private static final Pattern CLOCK = Pattern.compile(
            "(上午|下午|晚上|中午|凌晨|早上|早晨)?\\s*"
                    + "([0-9]{1,2}|[零〇一二两三四五六七八九十]{1,3})\\s*[点时]\\s*"
                    + "(半|一刻|三刻|[0-9]{1,2}|[零〇一二三四五六七八九十]{1,3})?\\s*分?");

    /** 冒号时间：9:30 / 21：05 */
    private static final Pattern COLON = Pattern.compile("([0-9]{1,2})\\s*[:：]\\s*([0-9]{1,2})");

    /**
     * 以今天为基准解析时间。
     *
     * @param text 用户文本
     * @return 解析出的时间；无法解析返回 null
     */
    public static LocalDateTime parse(String text) {
        return parse(text, LocalDate.now());
    }

    /**
     * 以指定日期为基准解析（便于测试固定"今天"）。
     *
     * @param text  用户文本
     * @param today 基准日期
     * @return 解析出的时间；无法解析返回 null
     */
    static LocalDateTime parse(String text, LocalDate today) {
        if (text == null || text.isBlank()) {
            return null;
        }
        LocalDate base = resolveDate(text, today);

        Integer hour;
        int minute;
        Matcher colon = COLON.matcher(text);
        if (colon.find()) {
            hour = Integer.valueOf(colon.group(1));
            minute = Integer.parseInt(colon.group(2));
        } else {
            Matcher clock = CLOCK.matcher(text);
            if (!clock.find()) {
                return null;
            }
            hour = parseNumber(clock.group(2));
            if (hour == null) {
                return null;
            }
            minute = parseMinute(clock.group(3));
        }
        int adjusted = adjustHour(hour, resolveMeridiem(text));
        if (adjusted < 0 || adjusted > 23 || minute < 0 || minute > 59) {
            return null;
        }
        return base.atTime(LocalTime.of(adjusted, minute));
    }

    /**
     * 解析日期词（大后天要先判断，否则会被"后天"抢先命中）。
     */
    static LocalDate resolveDate(String text, LocalDate today) {
        if (text.contains("大后天")) {
            return today.plusDays(3);
        }
        if (text.contains("后天")) {
            return today.plusDays(2);
        }
        if (text.contains("明天") || text.contains("明日")) {
            return today.plusDays(1);
        }
        return today;
    }

    /**
     * 解析时段前缀。
     *
     * @return AM / NOON / PM（下午）/ EVENING（晚上）；无时段返回 null
     */
    static String resolveMeridiem(String text) {
        if (text.contains("下午")) {
            return "PM";
        }
        if (text.contains("晚上")) {
            return "EVENING";
        }
        if (text.contains("中午")) {
            return "NOON";
        }
        if (text.contains("凌晨") || text.contains("早上") || text.contains("早晨") || text.contains("上午")) {
            return "AM";
        }
        return null;
    }

    /**
     * 12 小时制转 24 小时制。
     *
     * @param hour     原始小时（0-12）
     * @param meridiem 时段（见 {@link #resolveMeridiem(String)}）
     * @return 24 小时制小时
     */
    static int adjustHour(int hour, String meridiem) {
        if (meridiem == null) {
            return hour;
        }
        switch (meridiem) {
            case "PM":
                // 下午：1~11 点 +12；12 点保持 12
                return hour < 12 ? hour + 12 : 12;
            case "EVENING":
                // 晚上：1~11 点 +12；12 点视为 0 点（午夜）
                return hour < 12 ? hour + 12 : 0;
            case "NOON":
                // 中午：12 点保持；1~2 点按 13~14 点；11 点保持 11
                return hour == 12 ? 12 : (hour <= 2 ? hour + 12 : hour);
            case "AM":
                // 凌晨/上午：12 点视为 0 点
                return hour == 12 ? 0 : hour;
            default:
                return hour;
        }
    }

    /**
     * 解析中文/阿拉伯数字（0-99）。
     *
     * <p>中文规则：个位先暂存，「十」把暂存值（缺省 1）乘 10 累加，最后加上剩余个位。
     * 例：十=10、十二=12、二十=20、二十三=23、四=4、两=2。</p>
     *
     * @param s 数字文本
     * @return 数值；无法解析返回 null
     */
    static Integer parseNumber(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        if (s.matches("\\d+")) {
            return Integer.valueOf(s);
        }
        int total = 0;
        int unit = -1;
        for (char c : s.toCharArray()) {
            int digit = digitOf(c);
            if (digit >= 0) {
                unit = digit;
                continue;
            }
            if (c == '十') {
                total += (unit < 0 ? 1 : unit) * 10;
                unit = -1;
                continue;
            }
            return null;
        }
        if (unit >= 0) {
            total += unit;
        }
        return total;
    }

    /**
     * 解析分钟部分。
     */
    static int parseMinute(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        if ("半".equals(s)) {
            return 30;
        }
        if ("一刻".equals(s)) {
            return 15;
        }
        if ("三刻".equals(s)) {
            return 45;
        }
        Integer n = parseNumber(s);
        return n == null ? 0 : n;
    }

    /**
     * 单个中文数字字符 → 数值。
     */
    private static int digitOf(char c) {
        switch (c) {
            case '零':
            case '〇':
                return 0;
            case '一':
                return 1;
            case '二':
            case '两':
                return 2;
            case '三':
                return 3;
            case '四':
                return 4;
            case '五':
                return 5;
            case '六':
                return 6;
            case '七':
                return 7;
            case '八':
                return 8;
            case '九':
                return 9;
            default:
                return -1;
        }
    }
}
