package com.hospital.smartmedical.module.medicalnote.service;

import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNote;
import com.hospital.smartmedical.module.medicalnote.entity.MedicalNoteVersion;

import java.util.List;

public interface MedicalNoteService {

    /**
     * 医生请求 AI 生成预览病历（NOTE-00）
     */
    MedicalNote previewNote(Long consultId, Long doctorId);

    /**
     * 分页查询病历列表（按角色过滤）
     */
    PageResult<MedicalNote> listNotes(Long userId, String role, int page, int size);

    /**
     * 病历详情
     */
    MedicalNote getNote(Long id, Long userId, String role);

    /**
     * 医生修改病历（超过3次 modifiedCount 返回错误）
     */
    MedicalNote updateNote(Long id, Long doctorId, String draft);

    /**
     * 医生签名
     */
    void signNote(Long id, Long doctorId);

    /**
     * 获取版本历史
     */
    List<MedicalNoteVersion> listVersions(Long noteId);
}
