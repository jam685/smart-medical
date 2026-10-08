package com.hospital.smartmedical.module.visit.vo;

import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.visit.entity.Visit;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 就诊进度视图对象（VO）
 * 作用：在 sm_visit 原始字段基础上，拼接患者端列表/详情页所需的展示信息，
 *       包括医生姓名/职称、患者姓名、挂号时间、解析后的进度节点数组、当前节点高亮等，
 *       避免前端自行联表或解析 JSON；状态中文描述、当前/下一节点也由后端统一计算。
 */
@Data
public class VisitVO {

    /** 就诊进度主键 sm_visit.id */
    private Long id;

    /** 关联挂号单 ID sm_visit.appointment_id（可空，AI 问诊发起的就诊无挂号单） */
    private Long appointmentId;

    /** 患者 ID sm_visit.patient_id */
    private Long patientId;

    /** 患者姓名（联 sm_patient.real_name） */
    private String patientName;

    /** 医生 ID sm_visit.doctor_id */
    private Long doctorId;

    /** 接诊医生姓名（联 sm_doctor.real_name） */
    private String doctorName;

    /** 接诊医生职称（联 sm_doctor.title） */
    private String doctorTitle;

    /** 就诊科室 sm_visit.department */
    private String department;

    /** 就诊状态码：WAITING 候诊 / CALLING 叫号 / IN_PROGRESS 就诊中 / FINISHED 已完成 */
    private String status;

    /** 状态中文描述（候诊/叫号/就诊中/已完成），由后端按 status 映射，前端不写死 */
    private String statusDesc;

    /** 挂号单号（联 sm_appointment 按 AppointmentVO 规则生成，无关联挂号时为 null） */
    private String orderNo;

    /** 预约就诊时间（联 sm_appointment.appointment_time，yyyy-MM-dd HH:mm:ss，无挂号时为 null） */
    private String appointmentTime;

    /** 进度节点列表（解析自 sm_visit.progress_nodes JSON，按时间顺序） */
    private List<Map<String, Object>> progressNodes;

    /** 当前所处节点名称（进度数组最后一项的 name；空数组时返回"待叫号"） */
    private String currentNode;

    /** 下一预期节点名称（按状态机推导，用于前端"下一步"展示） */
    private String nextNode;

    /** 创建时间 sm_visit.created_at（yyyy-MM-dd HH:mm:ss） */
    private String createdAt;

    /** 最后更新时间 sm_visit.updated_at（yyyy-MM-dd HH:mm:ss） */
    private String updatedAt;

    /** 时间字段序列化模板 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将 Visit 实体联表信息组装为前端展示 VO
     *
     * @param visit      就诊进度实体（必传）
     * @param doctor     关联医生实体（可空，医生被删除时降级展示）
     * @param patient    关联患者实体（可空）
     * @param appt       关联挂号单实体（可空，AI 问诊发起的就诊无挂号单）
     * @param nodes      已解析的进度节点列表（可空，空时置为空数组）
     * @return 组装完成的 VisitVO
     */
    public static VisitVO of(Visit visit, Doctor doctor, Patient patient, Appointment appt,
                             List<Map<String, Object>> nodes) {
        VisitVO vo = new VisitVO();
        vo.setId(visit.getId());
        vo.setAppointmentId(visit.getAppointmentId());
        vo.setPatientId(visit.getPatientId());
        vo.setPatientName(patient != null ? patient.getRealName() : "未知患者");
        vo.setDoctorId(visit.getDoctorId());
        if (doctor != null) {
            vo.setDoctorName(doctor.getRealName());
            vo.setDoctorTitle(doctor.getTitle());
        } else {
            vo.setDoctorName("未知医生");
            vo.setDoctorTitle(null);
        }
        vo.setDepartment(visit.getDepartment());
        vo.setStatus(visit.getStatus());
        vo.setStatusDesc(descOfStatus(visit.getStatus()));
        // 挂号单号与就诊时间来自关联挂号实体
        if (appt != null) {
            vo.setOrderNo(AppointmentVO.buildOrderNoPublic(appt));
            vo.setAppointmentTime(appt.getAppointmentTime() == null ? null
                    : appt.getAppointmentTime().format(TIME_FMT));
        }
        // 进度节点：空兜底为空数组，避免前端渲染异常
        List<Map<String, Object>> safeNodes = nodes == null ? Collections.emptyList() : nodes;
        vo.setProgressNodes(safeNodes);
        // 当前节点取最后一项的 name；空数组视为尚未叫号
        if (!safeNodes.isEmpty()) {
            Object name = safeNodes.get(safeNodes.size() - 1).get("name");
            vo.setCurrentNode(name == null ? "" : name.toString());
        } else {
            vo.setCurrentNode("待叫号");
        }
        vo.setNextNode(nextNodeOfStatus(visit.getStatus()));
        vo.setCreatedAt(formatTime(visit.getCreatedAt()));
        vo.setUpdatedAt(formatTime(visit.getUpdatedAt()));
        return vo;
    }

    /**
     * 状态码 → 中文描述（前端不写死业务字典）
     *
     * @param status 状态码
     * @return 中文描述，未知状态兜底返回原值
     */
    public static String descOfStatus(String status) {
        if (status == null) return "未知";
        switch (status) {
            case "WAITING": return "候诊";
            case "CALLING": return "叫号";
            case "IN_PROGRESS": return "就诊中";
            case "FINISHED": return "已完成";
            default: return status;
        }
    }

    /**
     * 按当前状态推导下一预期节点（用于前端"下一步"提示，仅展示用，不约束状态机）
     *
     * @param status 当前状态
     * @return 下一节点名称，已完成或未知时返回 null
     */
    public static String nextNodeOfStatus(String status) {
        if (status == null) return null;
        switch (status) {
            case "WAITING": return "叫号";
            case "CALLING": return "就诊";
            case "IN_PROGRESS": return "完成";
            case "FINISHED": return null;
            default: return null;
        }
    }

    /**
     * LocalDateTime 安全格式化为字符串
     *
     * @param t 时间（可空）
     * @return yyyy-MM-dd HH:mm:ss 或 null
     */
    private static String formatTime(LocalDateTime t) {
        return t == null ? null : t.format(TIME_FMT);
    }
}
