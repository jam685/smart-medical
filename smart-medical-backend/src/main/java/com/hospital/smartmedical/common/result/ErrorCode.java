package com.hospital.smartmedical.common.result;

import lombok.Getter;

@Getter
public enum ErrorCode {
    PARAM_ERROR(6001, "参数错误"),
    UNAUTHORIZED(6002, "未认证或token无效"),
    FORBIDDEN(6003, "无权限"),
    NOT_FOUND(4040, "资源不存在"),
    CONFLICT(4090, "业务冲突"),
    BAD_REQUEST(400, "请求错误"),
    SERVER_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
