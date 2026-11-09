package com.hospital.smartmedical.common.trace;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

/**
 * 全链路 TraceId 过滤器（请求入口第一站）。
 *
 * <p>职责：
 * 1) 先看请求头 {@code X-Trace-Id} 有没有（跨服务链路续接），合法就用；
 * 2) 没有就自己生成（{毫秒时间戳前10位}{14位随机}，共 24 位纯数字，方便 grep）；
 * 3) 写入 SLF4J MDC（ThreadLocal），logback pattern 自动拼到每条日志；
 * 4) 同时塞 sessionId（请求参数）和 reqStartAt（起始时间戳，finally 里算耗时用）；
 * 5) 把 traceId 放回响应头 {@code X-Trace-Id}，前端能拿到；
 * 6) finally 里记 REQUEST DONE 日志（method + uri + status + 耗时ms）并清空 MDC，防止线程池串值。</p>
 *
 * <p>MDC 键位约定（logback-spring.xml pattern 统一用 %X{xxx} 读取）：
 * <pre>
 *   traceId  — 请求身份证号（必填）
 *   userId   — 当前登录用户（AuthInterceptor 验完 JWT 后回填，这里先给空串）
 *   role     — 当前角色（同上，PATIENT/DOCTOR/ADMIN）
 *   username — 用户名（同上）
 *   sessionId — 会话 ID（前端请求参数透传，非必填）
 * </pre></p>
 *
 * <p>注意：Filter 顺序早于 AuthInterceptor，所以 userId/role/username 这里先塞空串占位，
 * 由 AuthInterceptor 验签通过后再 {@link MDC#put(String, String)} 回填。</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    // ====== MDC 键位常量（全局唯一来源，logback pattern 和其他组件统一引用） ======
    public static final String MDC_TRACE_ID  = "traceId";
    public static final String MDC_USER_ID   = "userId";
    public static final String MDC_ROLE      = "role";
    public static final String MDC_USERNAME  = "username";
    public static final String MDC_SESSION   = "sessionId";
    public static final String MDC_START_AT  = "reqStartAt";

    /** 前端/上游服务透传的 header 名（跨服务续接 trace 用） */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** traceId 最大长度（防御性校验，超过视为非法重生成） */
    private static final int TRACE_ID_MAX_LEN = 64;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest  request  = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // ====== 1) 生成 / 接收 traceId ======
        String traceId = request.getHeader(HEADER_TRACE_ID);
        if (traceId == null || traceId.isBlank() || traceId.length() > TRACE_ID_MAX_LEN) {
            traceId = generateTraceId();
        }

        // ====== 2) 注入 MDC（先给空串占位，AuthInterceptor 验完 JWT 后回填 userId/role/username） ======
        long startNanos = System.nanoTime();
        MDC.put(MDC_TRACE_ID,  traceId);
        MDC.put(MDC_USER_ID,   "");
        MDC.put(MDC_ROLE,      "");
        MDC.put(MDC_USERNAME,  "");
        // sessionId 从请求参数拿（前端问诊时传的）
        String sidParam = request.getParameter("sessionId");
        MDC.put(MDC_SESSION,   sidParam == null ? "" : sidParam);
        MDC.put(MDC_START_AT,  String.valueOf(startNanos));

        // ====== 3) 回写响应头（前端能拿到 traceId） ======
        response.setHeader(HEADER_TRACE_ID, traceId);

        try {
            chain.doFilter(req, res);
        } finally {
            // ====== 4) 记耗时 + 清理 MDC ======
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("REQUEST DONE {} {} status={} costMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMs);
            // ThreadLocal 必须清理！Tomcat 线程池复用，不清理下一个请求会串上一个用户的 userId
            MDC.clear();
        }
    }

    /**
     * 生成 24 位纯数字 traceId：{毫秒时间戳前10位}{14位随机数字}。
     * 优点：同一毫秒内随机碰撞概率极低；纯数字方便 grep；时间戳天然带排序性。
     */
    private String generateTraceId() {
        long ts   = Instant.now().toEpochMilli();
        String rand = UUID.randomUUID().toString().replace("-", "").substring(10, 24);
        // 确保 14 位都是数字（UUID 子串可能含字母）：用时间戳模运算转数字
        long randNum = Math.abs(UUID.randomUUID().getMostSignificantBits()) % 10_000_000_000_000L;
        return String.valueOf(ts) + String.format("%014d", randNum);
    }
}
