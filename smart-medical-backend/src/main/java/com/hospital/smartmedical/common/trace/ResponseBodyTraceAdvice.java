package com.hospital.smartmedical.common.trace;

import com.hospital.smartmedical.common.result.Result;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 自动给所有 {@link Result} 响应体注入 traceId（从 MDC 取）。
 *
 * <p>效果：前端收到的 JSON 里会包含 {@code "traceId": "1733512345abcd..."} 字段，
 * 用户报 bug 时直接把这个号贴给运维，grep 一捞就是整条链路的日志。</p>
 *
 * <p>为什么加 @Order(Ordered.HIGHEST_PRECEDENCE)：
 * ResponseBodyAdvice 可能有多个（比如业务层的加密/脱敏 Advice），
 * traceId 是最基础的可观测性字段，要在其他 Advice 之前就填好。</p>
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ResponseBodyTraceAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // 对所有响应体生效（但 beforeBodyWrite 里只处理 Result 类型）
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result) {
            String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
            // traceId 理论上总是非空（TraceIdFilter 必定注入），这里兜底处理
            if (traceId != null && !traceId.isBlank()) {
                result.setTraceId(traceId);
            }
        }
        return body;
    }
}
