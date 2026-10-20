package com.hospital.smartmedical.module.medicalnote.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.common.result.ErrorCode;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNote;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNoteVersion;
import com.hospital.smartmedical.module.medicalnote.mapper.MedicalNoteMapper;
import com.hospital.smartmedical.module.medicalnote.mapper.MedicalNoteVersionMapper;
import com.hospital.smartmedical.module.medicalnote.service.MedicalNoteService;
import com.hospital.smartmedical.module.onlineconsult.entity.OnlineConsultRecord;
import com.hospital.smartmedical.module.onlineconsult.mapper.OnlineConsultRecordMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MedicalNoteServiceImpl implements MedicalNoteService {

    private final MedicalNoteMapper medicalNoteMapper;
    private final MedicalNoteVersionMapper medicalNoteVersionMapper;
    private final OnlineConsultRecordMapper onlineConsultMapper;
    private final PatientMapper patientMapper;

    private static final String STATUS_PREVIEW = "PREVIEW";
    private static final String STATUS_SIGNED = "SIGNED";

    // ===================== 1. AI 生成预览病历 =====================

    @Override
    @Transactional
    public MedicalNote previewNote(Long consultId, Long doctorId) {
        OnlineConsultRecord consult = onlineConsultMapper.selectById(consultId);
        if (consult == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Patient patient = patientMapper.selectById(consult.getPatientId());

        // 若已存在则直接返回
        LambdaQueryWrapper<MedicalNote> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(MedicalNote::getConsultId, consultId);
        MedicalNote exist = medicalNoteMapper.selectOne(existWrapper);
        if (exist != null) {
            return exist;
        }

        MedicalNote note = new MedicalNote();
        note.setConsultId(consultId);
        note.setPatientId(consult.getPatientId());
        note.setDoctorId(doctorId);
        note.setStatus(STATUS_PREVIEW);
        note.setModifiedCount(0);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        // Demo: 生成一个 NOTE-00 风格的病历预览文本
        String patientName = patient != null ? patient.getRealName() : "患者";
        int age = 30;
        if (patient != null && patient.getIdCard() != null && patient.getIdCard().length() >= 14) {
            try {
                age = LocalDateTime.now().getYear() - Integer.parseInt(patient.getIdCard().substring(6, 10));
            } catch (Exception ignored) {}
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【NOTE-00 门诊病历预览】\n");
        sb.append("患者：").append(patientName).append("　年龄：").append(age).append("\n");
        sb.append("科别：").append(consult.getDepartment() != null ? consult.getDepartment() : "未填写").append("\n");
        sb.append("主诉：待补充\n");
        sb.append("现病史：待补充\n");
        sb.append("初步诊断：待进一步明确\n");
        sb.append("处理建议：建议完善相关检查，根据检查结果制定治疗方案。\n");
        sb.append("———— 以下由医生补充 ————\n");
        sb.append("既往史：\n");
        sb.append("过敏史：\n");
        sb.append("体格检查：\n");
        sb.append("辅助检查：\n");
        sb.append("诊断：\n");
        sb.append("处置：\n");
        note.setDraft(sb.toString());

        medicalNoteMapper.insert(note);

        // 初始化版本记录（V1.3 精简：仅记录版本轨迹，无内容列）
        MedicalNoteVersion v = new MedicalNoteVersion();
        v.setNoteId(note.getId());
        v.setVersion(1);
        v.setModifiedBy(doctorId);
        v.setCreatedAt(LocalDateTime.now());
        medicalNoteVersionMapper.insert(v);

        return note;
    }

    // ===================== 2. 病历列表（分页） =====================

    @Override
    public PageResult<MedicalNote> listNotes(Long userId, String role, int page, int size) {
        Page<MedicalNote> pageParam = Page.of(page, size);
        LambdaQueryWrapper<MedicalNote> wrapper = new LambdaQueryWrapper<>();

        Role r = Role.of(role);
        if (r == Role.PATIENT) {
            // 患者只返回自己的 SIGNED
            wrapper.eq(MedicalNote::getPatientId, userId);
            wrapper.eq(MedicalNote::getStatus, STATUS_SIGNED);
        } else if (r == Role.DOCTOR) {
            // 医生返回自己写的
            wrapper.eq(MedicalNote::getDoctorId, userId);
        }
        // ADMIN 返回全部

        wrapper.orderByDesc(MedicalNote::getCreatedAt);
        Page<MedicalNote> result = medicalNoteMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    // ===================== 3. 病历详情 =====================

    @Override
    public MedicalNote getNote(Long id, Long userId, String role) {
        MedicalNote note = medicalNoteMapper.selectById(id);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Role r = Role.of(role);
        if (r == Role.PATIENT) {
            // 患者只能看 SIGNED
            if (!STATUS_SIGNED.equals(note.getStatus())) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
            if (!note.getPatientId().equals(userId)) {
                throw new BizException(ErrorCode.FORBIDDEN);
            }
        }
        return note;
    }

    // ===================== 4. 修改病历 =====================

    @Override
    @Transactional
    public MedicalNote updateNote(Long id, Long doctorId, String draft) {
        MedicalNote note = medicalNoteMapper.selectById(id);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!note.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (STATUS_SIGNED.equals(note.getStatus())) {
            throw new BizException(ErrorCode.CONFLICT);
        }

        int modifiedCount = note.getModifiedCount() == null ? 0 : note.getModifiedCount();
        if (modifiedCount >= 3) {
            throw new BizException(4090, "病历修改超过3次，已作废");
        }

        // 保存当前版本（V1.3 精简：仅记录版本轨迹，无内容列）
        MedicalNoteVersion v = new MedicalNoteVersion();
        v.setNoteId(note.getId());
        v.setVersion(modifiedCount + 1);
        v.setModifiedBy(doctorId);
        v.setCreatedAt(LocalDateTime.now());
        medicalNoteVersionMapper.insert(v);

        // 更新
        note.setDraft(draft);
        note.setModifiedCount(modifiedCount + 1);
        note.setUpdatedAt(LocalDateTime.now());
        medicalNoteMapper.updateById(note);

        return note;
    }

    // ===================== 5. 签名 =====================

    @Override
    public void signNote(Long id, Long doctorId) {
        MedicalNote note = medicalNoteMapper.selectById(id);
        if (note == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!note.getDoctorId().equals(doctorId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        LambdaUpdateWrapper<MedicalNote> upd = new LambdaUpdateWrapper<>();
        upd.eq(MedicalNote::getId, id);
        upd.set(MedicalNote::getStatus, STATUS_SIGNED);
        upd.set(MedicalNote::getSignedAt, LocalDateTime.now());
        upd.set(MedicalNote::getUpdatedAt, LocalDateTime.now());
        medicalNoteMapper.update(null, upd);
    }

    // ===================== 6. 版本历史 =====================

    @Override
    public List<MedicalNoteVersion> listVersions(Long noteId) {
        LambdaQueryWrapper<MedicalNoteVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MedicalNoteVersion::getNoteId, noteId);
        wrapper.orderByAsc(MedicalNoteVersion::getVersion);
        return medicalNoteVersionMapper.selectList(wrapper);
    }
}
