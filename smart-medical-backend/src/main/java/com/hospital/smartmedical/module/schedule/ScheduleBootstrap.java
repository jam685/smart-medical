package com.hospital.smartmedical.module.schedule;

import com.hospital.smartmedical.module.schedule.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 号源启动兜底：空库时自动为医生生成近 7 天号源，保证"患者选号"开箱可用。
 *
 * <p>可用 app.schedule.auto-seed=false 关闭；已有号源时不会重复生成，也不会覆盖停诊/加号。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleBootstrap implements ApplicationRunner {

    private final ScheduleService scheduleService;

    /**
     * 是否在空库时自动播种号源。
     *
     * <p>默认 false：生产环境不应由应用自动生成"排班"，号源必须来自管理端真实排班；
     * 本地演示可用 {@code --spring.profiles.active=demo}（application-demo.yml 会打开）。</p>
     */
    @Value("${app.schedule.auto-seed:false}")
    private boolean autoSeed;

    @Override
    public void run(ApplicationArguments args) {
        if (!autoSeed) {
            return;
        }
        try {
            scheduleService.seedIfEmpty(7, 5);
        } catch (Exception e) {
            // 兜底逻辑失败不能影响主流程启动（例如表还没建）
            log.warn("号源自动生成跳过：{}", e.getMessage());
        }
    }
}
