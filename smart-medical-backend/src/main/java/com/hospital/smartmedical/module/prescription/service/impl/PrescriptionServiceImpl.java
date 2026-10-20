package com.hospital.smartmedical.module.prescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.prescription.entity.Prescription;
import com.hospital.smartmedical.module.prescription.mapper.PrescriptionMapper;
import com.hospital.smartmedical.module.prescription.service.PrescriptionService;
import com.hospital.smartmedical.module.prescription.vo.PrescriptionVO;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 处方业务服务实现。
 *
 * <p>V1.3 处方表与明细表合并：一份处方多个药品明细 = 同一 prescription_no 的多行记录。
 * RX-01 生成 prescription_no 后逐条插入；RX-02/RX-03 按就诊/按医生查询并分组返回。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    /** 处方表 Mapper */
    private final PrescriptionMapper prescriptionMapper;
    /** 就诊进度 Mapper：校验就诊归属 */
    private final VisitMapper visitMapper;
    /** 医生 Mapper：联查医生姓名 */
    private final DoctorMapper doctorMapper;
    /** 患者 Mapper：联查患者姓名 */
    private final PatientMapper patientMapper;

    /** 处方编号前缀 */
    private static final String RX_PREFIX = "RX";

    /**
     * 生成处方编号：RX + yyyyMMddHHmmssSSS（Demo 唯一性足够）。
     */
    private String genPrescriptionNo() {
        return RX_PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }

    /**
     * 将一组同一 prescription_no 的处方行记录组装为 VO（带姓名）。
     */
    private PrescriptionVO buildVo(List<Prescription> rows) {
        Long patientId = rows.get(0).getPatientId();
        Long doctorId = rows.get(0).getDoctorId();
        String patientName = patientId == null ? null : patientMapper.selectById(patientId).getRealName();
        String doctorName = doctorId == null ? null : doctorMapper.selectById(doctorId).getRealName();
        return PrescriptionVO.of(rows, patientName, doctorName);
    }

    /**
     * 创建处方（RX-01）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrescriptionVO createPrescription(Long visitId, String diagnosis, String advice,
                                              List<PrescriptionVO.Item> items, Long userId, Role role) {
        // 权限：仅医生/管理员可开方
        if (role != Role.DOCTOR && role != Role.ADMIN) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        // 校验就诊存在
        Visit visit = visitMapper.selectById(visitId);
        if (visit == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        // 医生只能给本人接诊的患者开方
        if (role == Role.DOCTOR && !visit.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        // 至少一种药品
        if (CollectionUtils.isEmpty(items)) {
            throw new BizException("处方至少包含一种药品");
        }

        String prescriptionNo = genPrescriptionNo();
        List<Prescription> rows = new ArrayList<>();
        for (PrescriptionVO.Item it : items) {
            Prescription p = new Prescription();
            p.setPrescriptionNo(prescriptionNo);
            p.setVisitId(visitId);
            p.setAppointmentId(visit.getAppointmentId());
            p.setPatientId(visit.getPatientId());
            p.setDoctorId(visit.getDoctorId());
            p.setDepartment(visit.getDepartment());
            p.setDiagnosis(diagnosis);
            p.setAdvice(advice);
            p.setStatus("ISSUED");
            p.setDrugName(it.getDrugName());
            p.setSpecification(it.getSpecification());
            p.setDosage(it.getDosage());
            p.setFrequency(it.getFrequency());
            p.setDays(it.getDays());
            p.setQuantity(it.getQuantity());
            p.setUsageMethod(it.getUsageMethod());
            p.setRemark(it.getRemark());
            p.setCreatedAt(LocalDateTime.now());
            p.setUpdatedAt(LocalDateTime.now());
            prescriptionMapper.insert(p);
            rows.add(p);
        }

        log.info("处方开具成功: prescriptionNo={}, visitId={}, doctorId={}, items={}",
                prescriptionNo, visitId, userId, rows.size());
        return buildVo(rows);
    }

    /**
     * 按就诊 ID 查询处方（RX-02）。
     */
    @Override
    public PrescriptionVO getByVisitId(Long visitId, Long userId, Role role) {
        List<Prescription> list = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>().eq(Prescription::getVisitId, visitId));
        if (list.isEmpty()) {
            return null;
        }
        Prescription first = list.get(0);
        // 归属校验：患者只能看本人处方，医生只能看本人开方
        if (role == Role.PATIENT && !first.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !first.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return buildVo(list);
    }

    /**
     * 按医生查询处方列表（RX-03）。
     */
    @Override
    public List<PrescriptionVO> listByDoctor(Long doctorId) {
        if (doctorId == null) {
            return Collections.emptyList();
        }
        List<Prescription> list = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getDoctorId, doctorId)
                        .orderByDesc(Prescription::getCreatedAt));
        // 按 prescription_no 分组返回
        Set<String> noSet = new LinkedHashSet<>();
        for (Prescription p : list) {
            noSet.add(p.getPrescriptionNo());
        }
        List<PrescriptionVO> result = new ArrayList<>();
        for (String no : noSet) {
            List<Prescription> rows = new ArrayList<>();
            for (Prescription p : list) {
                if (no.equals(p.getPrescriptionNo())) {
                    rows.add(p);
                }
            }
            result.add(buildVo(rows));
        }
        return result;
    }

    /**
     * 按患者查询本人处方列表（RX-04）。
     */
    @Override
    public List<PrescriptionVO> listByPatient(Long patientId) {
        if (patientId == null) {
            return Collections.emptyList();
        }
        List<Prescription> list = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getPatientId, patientId)
                        .orderByDesc(Prescription::getCreatedAt));
        // 按 prescription_no 分组返回
        Set<String> noSet = new LinkedHashSet<>();
        for (Prescription p : list) {
            noSet.add(p.getPrescriptionNo());
        }
        List<PrescriptionVO> result = new ArrayList<>();
        for (String no : noSet) {
            List<Prescription> rows = new ArrayList<>();
            for (Prescription p : list) {
                if (no.equals(p.getPrescriptionNo())) {
                    rows.add(p);
                }
            }
            result.add(buildVo(rows));
        }
        return result;
    }
}