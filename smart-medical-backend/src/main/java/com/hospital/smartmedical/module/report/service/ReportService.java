package com.hospital.smartmedical.module.report.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.report.entity.Report;
import org.springframework.web.multipart.MultipartFile;

public interface ReportService {

    PageResult<Report> listReports(Long userId, Role role, int page, int size);

    Report getReportDetail(Long id, Long userId, Role role);

    Report upload(MultipartFile file, Long userId);
}
