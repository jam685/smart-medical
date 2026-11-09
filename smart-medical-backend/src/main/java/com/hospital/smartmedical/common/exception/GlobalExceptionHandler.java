package com.hospital.smartmedical.common.exception;

import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.common.trace.TraceIdFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        log.warn("BizException [traceId={}]: {}", traceId, e.getMessage());
        Result<Void> r = Result.fail(e.getCode(), e.getMessage());
        r.setTraceId(traceId);
        return r;
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<Void> handleValid(Exception e) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        log.warn("参数校验失败 [traceId={}]: {}", traceId, e.getMessage());
        Result<Void> r = Result.fail(ErrorCode.PARAM_ERROR);
        r.setTraceId(traceId);
        return r;
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        log.error("Unhandled exception [traceId={}]", traceId, e);
        Result<Void> r = Result.fail(ErrorCode.SERVER_ERROR);
        r.setTraceId(traceId);
        return r;
    }
}
