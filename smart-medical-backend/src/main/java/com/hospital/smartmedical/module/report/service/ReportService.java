package com.hospital.smartmedical.module.report.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.report.entity.Report;
import com.hospital.smartmedical.module.report.entity.ReportInterpretation;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ReportService {

    PageResult<Report> listReports(Long userId, Role role, int page, int size);

    Report getReportDetail(Long id, Long userId, Role role);

    Report upload(MultipartFile file, Long userId);

    /**
     * 获取解读结果。患者端过滤掉 confidence 和 references；医生/管理员返回完整。
     * 如尚未生成解读返回 null。
     */
    ReportInterpretation getInterpretation(Long reportId, Long userId, Role role);
}
