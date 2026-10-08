package com.hospital.smartmedical.common.exception;

import com.hospital.smartmedical.common.result.ErrorCode;
import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;
    public BizException(ErrorCode error) {
        super(error.getMessage());
        this.code = error.getCode();
    }
    public BizException(ErrorCode error, String message) {
        super(message);
        this.code = error.getCode();
    }
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
    public BizException(String message) {
        super(message);
        this.code = ErrorCode.BAD_REQUEST.getCode();
    }
}
