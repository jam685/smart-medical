package com.hospital.smartmedical.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 演示模式的 profile 隔离测试。
 *
 * <p>回归点：万能口令曾对所有环境生效（任何账号可用 123456 登录，且可作为"原密码"改密）。
 * 现在它被收进 {@code @Profile("demo")} 的 {@link DemoMode}，默认环境不存在该 Bean。</p>
 */
class DemoModeProfileTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(DemoMode.class);

    @Test
    @DisplayName("默认环境（无 profile）：DemoMode 不存在，万能口令不生效")
    void demoModeAbsentByDefault() {
        runner.run(context -> assertThat(context).doesNotHaveBean(DemoMode.class));
    }

    @Test
    @DisplayName("demo profile：DemoMode 存在，演示口令可用")
    void demoModePresentUnderDemoProfile() {
        runner.withPropertyValues("spring.profiles.active=demo")
                .run(context -> {
                    assertThat(context).hasSingleBean(DemoMode.class);
                    DemoMode demo = context.getBean(DemoMode.class);
                    assertThat(demo.matches("123456")).isTrue();
                    assertThat(demo.matches("wrong-password")).isFalse();
                    assertThat(demo.defaultPassword()).isEqualTo("123456");
                });
    }
}
