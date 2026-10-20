package com.hospital.smartmedical.common.security;

import com.hospital.smartmedical.common.exception.BizException;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码服务（内存版）。
 *
 * <p>用于"忘记密码"身份验证：按业务键（如用户名）生成 6 位数字验证码，
 * 有效期 5 分钟，验证后立即失效。演示环境未接入真实短信通道，
 * 验证码通过接口返回值 + 服务端日志透出。</p>
 */
public final class VerificationCodeService {

    /** 验证码有效时长（毫秒）：5 分钟 */
    private static final long EXPIRE_MS = 5 * 60 * 1000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, CodeEntry> STORE = new ConcurrentHashMap<>();

    private VerificationCodeService() {
    }

    /** 验证码条目：码值 + 过期时间戳 */
    private static class CodeEntry {
        final String code;
        final long expireAt;

        CodeEntry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    /**
     * 为指定业务键生成验证码（覆盖旧码）。
     *
     * @param key 业务键（如患者用户名）
     * @return 6 位数字验证码
     */
    public static String generate(String key) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        STORE.put(key, new CodeEntry(code, System.currentTimeMillis() + EXPIRE_MS));
        return code;
    }

    /**
     * 校验验证码：不存在/过期/不匹配均视为校验失败；成功后立即删除防重放。
     *
     * @param key  业务键（如患者用户名）
     * @param code 用户输入的验证码
     */
    public static void verify(String key, String code) {
        if (code == null || code.isBlank()) {
            throw new BizException("请输入验证码");
        }
        CodeEntry entry = STORE.get(key);
        if (entry == null || System.currentTimeMillis() > entry.expireAt) {
            STORE.remove(key);
            throw new BizException("验证码已过期，请重新获取");
        }
        if (!entry.code.equalsIgnoreCase(code.trim())) {
            throw new BizException("验证码不正确");
        }
        STORE.remove(key);
    }
}
