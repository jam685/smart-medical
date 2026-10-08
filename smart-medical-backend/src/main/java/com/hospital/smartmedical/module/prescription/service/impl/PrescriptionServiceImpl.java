package com.hospital.smartmedical.module.prescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.prescription.entity.Prescription;
import com.hospital.smartmedical.module.prescription.entity.PrescriptionItem;
import com.hospital.smartmedical.module.prescription.mapper.PrescriptionItemMapper;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处方业务服务实现
 * 事务：createPrescription 主表+明细同事务写入，任一失败回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    /** 处方主表 Mapper */
    private final PrescriptionMapper prescriptionMapper;
    /** 处方明细 Mapper */
    private final PrescriptionItemMapper prescriptionItemMapper;
    /** 就诊进度 Mapper：校验就诊归属 */
    private final VisitMapper visitMapper;
    /** 医生 Mapper：联查医生姓名 */
    private final DoctorMapper doctorMapper;
    /** 患者 Mapper：联查患者姓名 */
    private final PatientMapper patientMapper;

    /**
     * 创建处方（医生接诊时开具）
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

        // 组装处方主表
        Prescription p = new Prescription();
        p.setVisitId(visitId);
        p.setAppointmentId(visit.getAppointmentId());
        p.setPatientId(visit.getPatientId());
        p.setDoctorId(visit.getDoctorId());
        p.setDepartment(visit.getDepartment());
        p.setDiagnosis(diagnosis);
        p.setAdvice(advice);
        p.setStatus("ISSUED");
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());
        prescriptionMapper.insert(p);

        // 批量插入药品明细
        List<PrescriptionItem> itemList = items.stream().map(it -> {
            PrescriptionItem pi = new PrescriptionItem();
            pi.setPrescriptionId(p.getId());
            pi.setDrugName(it.getDrugName());
            pi.setSpecification(it.getSpecification());
            pi.setDosage(it.getDosage());
            pi.setFrequency(it.getFrequency());
            pi.setDays(it.getDays());
            pi.setQuantity(it.getQuantity());
            pi.setUsageMethod(it.getUsageMethod());
            pi.setRemark(it.getRemark());
            pi.setCreatedAt(LocalDateTime.now());
            return pi;
        }).collect(Collectors.toList());
        itemList.forEach(prescriptionItemMapper::insert);

        log.info("处方开具成功: prescriptionId={}, visitId={}, doctorId={}", p.getId(), visitId, userId);
        return PrescriptionVO.of(p, itemList, null, null);
    }

    /**
     * 按就诊 ID 查询处方详情（含明细）
     */
    @Override
    public PrescriptionVO getByVisitId(Long visitId, Long userId, Role role) {
        List<Prescription> list = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>().eq(Prescription::getVisitId, visitId));
        if (list.isEmpty()) {
            return null;
        }
        Prescription p = list.get(0);
        // 归属校验：患者只能看本人处方，医生只能看本人开方
        if (role == Role.PATIENT && !p.getPatientId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (role == Role.DOCTOR && !p.getDoctorId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>().eq(PrescriptionItem::getPrescriptionId, p.getId()));
        String patientName = p.getPatientId() == null ? null
                : patientMapper.selectById(p.getPatientId()).getRealName();
        String doctorName = p.getDoctorId() == null ? null
                : doctorMapper.selectById(p.getDoctorId()).getRealName();
        return PrescriptionVO.of(p, items, patientName, doctorName);
    }

    /**
     * 查询指定医生开具的处方列表
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
        return list.stream().map(p -> {
            String patientName = p.getPatientId() == null ? null
                    : patientMapper.selectById(p.getPatientId()).getRealName();
            String doctorName = p.getDoctorId() == null ? null
                    : doctorMapper.selectById(p.getDoctorId()).getRealName();
            return PrescriptionVO.of(p, Collections.emptyList(), patientName, doctorName);
        }).collect(Collectors.toList());
    }
}
