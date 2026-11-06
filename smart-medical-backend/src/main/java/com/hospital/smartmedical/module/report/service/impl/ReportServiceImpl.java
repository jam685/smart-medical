package com.hospital.smartmedical.module.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.common.storage.FileStorageService;
import com.hospital.smartmedical.module.report.entity.Report;
import com.hospital.smartmedical.module.report.mapper.ReportMapper;
import com.hospital.smartmedical.module.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportMapper reportMapper;
    private final FileStorageService fileStorageService;

    @Override
    public PageResult<Report> listReports(Long userId, Role role, int page, int size) {
        LambdaQueryWrapper<Report> qw = new LambdaQueryWrapper<>();
        if (role == Role.PATIENT) {
            qw.eq(Report::getPatientId, userId);
        } else if (role == Role.DOCTOR) {
            qw.eq(Report::getUploadedBy, userId);
        }
        qw.orderByDesc(Report::getCreatedAt);

        Page<Report> pageParam = Page.of(Math.max(1, page), Math.max(1, size));
        Page<Report> result = reportMapper.selectPage(pageParam, qw);
        return PageResult.of(result.getTotal(), result.getRecords(), (int) result.getCurrent(), (int) result.getSize());
    }

    @Override
    public Report getReportDetail(Long id, Long userId, Role role) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (role == Role.PATIENT && !report.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !report.getUploadedBy().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return report;
    }

    @Override
    public Report upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }

        // 真实保存图片到 uploads/reports/，返回可直接访问的 URL（含 context-path）
        String imageUrl = fileStorageService.saveImage(file, "reports");

        Report report = new Report();
        report.setImageUrl(imageUrl);
        report.setUploadedBy(userId);
        report.setPatientId(userId);
        report.setStatus("UPLOADED");
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
        return report;
    }
}
