package com.hospital.smartmedical.common.result;

import lombok.Data;

@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.setCode(0);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }

    public static <T> Result<T> fail(ErrorCode error) {
        return fail(error.getCode(), error.getMessage());
    }
}
