package com.hospital.smartmedical.common.util;

/**
 * 日志脱敏工具类。
 *
 * <p>用途：日志文件中禁止出现明文手机号、身份证号、邮箱，以及过长的用户输入（病历/对话内容）。
 * 所有业务代码里打敏感信息日志时，必须先用这个工具处理。</p>
 *
 * <p>设计原则：所有方法都做 null / 空串兜底，调用方不用额外判空。</p>
 */
public final class MaskUtils {

    private MaskUtils() {
        // 禁止实例化
    }

    /**
     * 手机号脱敏：11 位号码中间 4 位替换成 ****。
     * 长度不足 11 位或含非数字字符，原样返回（不做猜测性处理）。
     *
     * @param phone 手机号
     * @return 脱敏后的字符串，null 输入返回 ""
     */
    public static String phone(String phone) {
        if (phone == null || phone.isBlank()) return "";
        if (phone.length() != 11) return phone;
        if (!phone.matches("\\d{11}")) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 身份证号脱敏：18 位号码保留前 3 位与后 4 位，中间 **** 填充。
     * 15 位同理。长度不足原样返回。
     *
     * @param idCard 身份证号
     * @return 脱敏后的字符串，null 输入返回 ""
     */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.isBlank()) return "";
        int len = idCard.length();
        if (len == 18) {
            return idCard.substring(0, 3) + "***********" + idCard.substring(14);
        }
        if (len == 15) {
            return idCard.substring(0, 3) + "********" + idCard.substring(11);
        }
        return idCard;
    }

    /**
     * 邮箱脱敏：保留 @ 前第一位和 @ 后面的完整域名，中间 **** 填充。
     *
     * @param email 邮箱
     * @return 脱敏后的字符串，null 输入返回 ""
     */
    public static String email(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "****" + email.substring(at);
    }

    /**
     * 通用文本截断：日志里打用户输入/对话内容时，只保留前 maxLen 个字符，防止一大段病历/对话内容进日志文件。
     *
     * @param text   原始文本
     * @param maxLen 最大保留长度（建议 50~100）
     * @return 截断后的字符串，超过 maxLen 时追加 "..." 标记，null 输入返回 ""
     */
    public static String truncate(String text, int maxLen) {
        if (text == null || text.isBlank()) return "";
        if (maxLen <= 0) return text;
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 姓名脱敏：保留姓（第一个字符）和最后一个字符，中间用 * 填充。
     * 单字姓名原样返回。
     *
     * @param name 姓名
     * @return 脱敏后的字符串
     */
    public static String name(String name) {
        if (name == null || name.isBlank()) return "";
        int len = name.length();
        if (len <= 1) return name;
        if (len == 2) return name.charAt(0) + "*";
        StringBuilder sb = new StringBuilder();
        sb.append(name.charAt(0));
        sb.append("*".repeat(Math.max(0, len - 2)));
        sb.append(name.charAt(len - 1));
        return sb.toString();
    }
}
