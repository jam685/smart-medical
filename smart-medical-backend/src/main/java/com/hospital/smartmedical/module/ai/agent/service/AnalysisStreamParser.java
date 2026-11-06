package com.hospital.smartmedical.module.ai.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;

/**
 * 问诊分析 JSON 的增量解析器：随模型流式输出的文本块逐字段解析，
 * 顶层字段一旦闭合立即回调，用于前端分节渐进渲染（无需等待整段 JSON）。
 *
 * <p>约定：模型按固定字段顺序输出单个 JSON 对象；解析器容忍 ```json 包裹与首尾杂文本，
 * 并对文本字段 draftReply 追加推送增量片段（前端据此实现打字机效果）。</p>
 */
public class AnalysisStreamParser {

    /** 字段完成 / 文本增量回调 */
    public interface Listener {
        /**
         * 顶层字段解析完成。
         *
         * @param key   字段名
         * @param value 该字段的 JSON 节点
         */
        void onField(String key, JsonNode value);

        /**
         * 文本字段增量片段（当前仅 draftReply）。
         *
         * @param delta 本次新增的可直接展示文本
         */
        void onToken(String delta);
    }

    /** 需要边生成边推送增量的文本字段 */
    private static final Set<String> TEXT_STREAM_KEYS = Set.of("draftReply");

    private final ObjectMapper objectMapper;
    private final Listener listener;

    /** 已接收的全部模型输出 */
    private final StringBuilder buffer = new StringBuilder();

    /** 已推送完成的顶层字段 */
    private final Set<String> emittedKeys = new HashSet<>();

    /** 正在推送增量的文本字符串内容起始下标 */
    private int textContentStart = -1;

    /** 该文本字符串中已推送的原始字符数（相对内容起始位置） */
    private int textRawConsumed = 0;

    public AnalysisStreamParser(ObjectMapper objectMapper, Listener listener) {
        this.objectMapper = objectMapper;
        this.listener = listener;
    }

    /**
     * 接收模型增量文本块并尝试解析。
     */
    public synchronized void accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        buffer.append(chunk);
        scan();
    }

    /**
     * 流结束：做最后一次解析尝试。
     */
    public synchronized void finish() {
        scan();
    }

    /**
     * 返回当前已接收的完整原始输出。
     */
    public synchronized String raw() {
        return buffer.toString();
    }

    /**
     * 扫描缓冲区：从根对象起逐个顶层字段解析，已闭合即回调。
     * 采用全量重扫（缓冲区仅数 KB），避免维护复杂可恢复状态。
     */
    private void scan() {
        String text = buffer.toString();
        int len = text.length();
        int start = text.indexOf('{');
        if (start < 0) {
            return;
        }
        int i = start + 1;
        while (i < len) {
            // 跳过空白与字段分隔逗号
            while (i < len && isSkippable(text.charAt(i))) {
                i++;
            }
            if (i >= len) {
                return;
            }
            char c = text.charAt(i);
            if (c == '}') {
                return; // 根对象闭合，解析结束
            }
            if (c != '"') {
                return; // 结构异常，交给整体解析兜底
            }
            int keyEnd = findStringEnd(text, i);
            if (keyEnd < 0) {
                return; // key 尚未完整
            }
            String key = text.substring(i + 1, keyEnd);
            i = keyEnd + 1;
            while (i < len && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= len) {
                return;
            }
            if (text.charAt(i) != ':') {
                return;
            }
            i++;
            while (i < len && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            if (i >= len) {
                return;
            }

            int valueEnd = findValueEnd(text, i);
            if (valueEnd < 0) {
                // 值尚未闭合：文本字段边生成边推送增量
                if (TEXT_STREAM_KEYS.contains(key) && text.charAt(i) == '"') {
                    pushTextDelta(text, i + 1);
                }
                return;
            }
            if (!emittedKeys.contains(key)) {
                emittedKeys.add(key);
                JsonNode node = parseValue(text.substring(i, valueEnd));
                if (node != null) {
                    listener.onField(key, node);
                }
            }
            i = valueEnd;
        }
    }

    /**
     * 推送文本字段的增量片段（不切断转义序列，不把结尾引号当作内容）。
     */
    private void pushTextDelta(String text, int contentStart) {
        if (textContentStart != contentStart) {
            // 定位变化（首次进入或重新定位），重置消费进度
            textContentStart = contentStart;
            textRawConsumed = 0;
        }
        int len = text.length();
        int from = contentStart + textRawConsumed;
        if (from >= len) {
            return;
        }
        // 计算安全边界：完整转义序列之后、结束引号之前
        int safeEnd = len;
        for (int i = from; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                if (i + 1 >= len) {
                    safeEnd = i;
                    break;
                }
                if (text.charAt(i + 1) == 'u') {
                    if (i + 5 >= len) {
                        safeEnd = i;
                        break;
                    }
                    i += 5;
                } else {
                    i += 1;
                }
            } else if (c == '"') {
                safeEnd = i;
                break;
            }
        }
        if (safeEnd <= from) {
            return;
        }
        String delta = unescape(text.substring(from, safeEnd));
        textRawConsumed = safeEnd - contentStart;
        if (!delta.isEmpty()) {
            listener.onToken(delta);
        }
    }

    /**
     * JSON 字符串转义还原（支持换行/制表/引号/反斜杠/斜杠与十六进制转义）。
     */
    private String unescape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                sb.append(c);
                continue;
            }
            char n = raw.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 't' -> sb.append('\t');
                case 'r' -> sb.append('\r');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'u' -> {
                    if (i + 4 < raw.length()) {
                        try {
                            sb.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException e) {
                            sb.append('\\').append(n);
                        }
                    } else {
                        sb.append('\\').append(n);
                    }
                }
                default -> sb.append('\\').append(n);
            }
        }
        return sb.toString();
    }

    /**
     * 查找字符串结束引号下标（跳过转义）。
     *
     * @return 结束引号下标；未闭合返回 -1
     */
    private int findStringEnd(String text, int quoteIndex) {
        boolean escaped = false;
        for (int i = quoteIndex + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找字段值结束下标（返回值的结束位置，不含分隔符）。
     *
     * @return 值结束后的下标；未闭合返回 -1
     */
    private int findValueEnd(String text, int valueStart) {
        char c = text.charAt(valueStart);
        if (c == '"') {
            int end = findStringEnd(text, valueStart);
            return end < 0 ? -1 : end + 1;
        }
        if (c == '{' || c == '[') {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = valueStart; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (inString) {
                    if (escaped) {
                        escaped = false;
                    } else if (ch == '\\') {
                        escaped = true;
                    } else if (ch == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (ch == '"') {
                    inString = true;
                } else if (ch == '{' || ch == '[') {
                    depth++;
                } else if (ch == '}' || ch == ']') {
                    depth--;
                    if (depth == 0) {
                        return i + 1;
                    }
                }
            }
            return -1;
        }
        // 数字 / true / false / null：扫描到分隔符为止
        for (int i = valueStart; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ',' || ch == '}') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析单个字段值的 JSON。
     */
    private JsonNode parseValue(String raw) {
        try {
            return objectMapper.readTree(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isSkippable(char c) {
        return Character.isWhitespace(c) || c == ',';
    }
}
