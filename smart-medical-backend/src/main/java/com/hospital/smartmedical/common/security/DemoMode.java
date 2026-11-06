package com.hospital.smartmedical.common.security;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 演示模式支持（仅在 {@code demo} profile 下装配）。
 *
 * <p>背景：早期为了演示方便，登录逻辑里硬编码了一个"万能口令"（123456 的 BCrypt 哈希），
 * 任何账号都能用该口令登录；改密码接口甚至把它当作合法的"原密码"。
 * 这在任何非演示环境都是账号接管漏洞。</p>
 *
 * <p>现在做法：把这段能力收进 {@code @Profile("demo")} 的 Bean。
 * 默认 profile 下该 Bean 不存在，服务端只认数据库里的真实密码；
 * 只有显式以 {@code --spring.profiles.active=demo} 启动时才恢复演示便利。</p>
 */
@Component
@Profile("demo")
public class DemoMode {

    /** 演示口令明文（仅用于管理员新建账号时给出初始密码，不参与校验） */
    private static final String DEMO_PASSWORD = "123456";

    /** 演示口令的 BCrypt 哈希（123456） */
    private static final String DEMO_PASSWORD_HASH =
            "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi";

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 判断输入是否为演示口令。
     *
     * @param raw 用户输入的明文密码
     * @return true=命中演示口令（仅 demo profile 下可调用）
     */
    public boolean matches(String raw) {
        return raw != null && !raw.isEmpty() && passwordEncoder.matches(raw, DEMO_PASSWORD_HASH);
    }

    /**
     * 演示环境新建账号的默认初始密码。
     *
     * @return 固定演示口令
     */
    public String defaultPassword() {
        return DEMO_PASSWORD;
    }
}
