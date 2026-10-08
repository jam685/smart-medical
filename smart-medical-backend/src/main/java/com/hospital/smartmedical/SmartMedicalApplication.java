package com.hospital.smartmedical;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
@MapperScan("com.hospital.smartmedical.module.**.mapper")
public class SmartMedicalApplication {
    public static void main(String[] args) {
        SpringApplication.run(SmartMedicalApplication.class, args);
        System.out.println("========================================");
        System.out.println(" 智能医疗系统启动成功!");
        System.out.println(" 接口前缀: /api/smart-medical/v1");
        System.out.println(" 访问地址: http://localhost:8080/api/smart-medical/v1");
        System.out.println("========================================");
    }
}
