package com.hospital.smartmedical.module.appointment.vo;

import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import lombok.Data;

import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 挂号单视图对象（VO）
 * 作用：在 sm_appointment 原始字段基础上，拼接患者端列表/详情页需要的医生展示信息，
 *       避免前端自行联表；挂号费与挂号单号也由后端统一生成，前端不写死任何业务数据。
 */
@Data
public class AppointmentVO {

    /** 挂号单主键 sm_appointment.id */
    private Long id;

    /** 患者 ID sm_appointment.patient_id */
    private Long patientId;

    /** 医生 ID sm_appointment.doctor_id */
    private Long doctorId;

    /** 就诊科室 sm_appointment.department */
    private String department;

    /** 就诊时间 sm_appointment.appointment_time（yyyy-MM-dd HH:mm:ss） */
    private String appointmentTime;

    /** 号源 ID（空表示历史数据/无号源挂号） */
    private Long scheduleId;

    /** 时段内序号（叫号顺序），空表示历史数据 */
    private Integer queueNo;

    /** 就诊时段开始时间（yyyy-MM-dd HH:mm:ss） */
    private String slotStart;

    /** 挂号状态：PENDING 待确认 / CONFIRMED 待就诊 / FINISHED 已完成 / CANCELLED 已取消 */
    private String status;

    /** 取消原因：CANCEL 患者取消 / SUSPEND 医生停诊 / RESCHEDULE 改约（仅已取消时有值） */
    private String cancelReason;

    /** 改约来源挂号单 ID（本单由改约产生时非空） */
    private Long rescheduledFrom;

    /** 挂号创建时间（yyyy-MM-dd HH:mm:ss） */
    private String createdAt;

    /** 挂号单号（后端按创建日期+主键生成，如 GH20260917008） */
    private String orderNo;

    /** 接诊医生姓名（联 sm_doctor.real_name） */
    private String doctorName;

    /** 接诊医生职称（联 sm_doctor.title） */
    private String doctorTitle;

    /** 挂号费（元，后端按职称档位计算：主任医师50/副主任医师35/主治医师及其他20） */
    private Integer fee;

    /** 单号日期格式化模板 */
    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 时间字段序列化模板 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将挂号实体 + 医生信息组装为前端展示 VO
     *
     * @param appt   挂号实体（必传）
     * @param doctor 该挂号对应的医生实体（可能为 null，例如医生已被删除时降级展示）
     * @return 组装完成的 AppointmentVO
     */
    public static AppointmentVO of(Appointment appt, Doctor doctor) {
        AppointmentVO vo = new AppointmentVO();
        vo.setId(appt.getId());
        vo.setPatientId(appt.getPatientId());
        vo.setDoctorId(appt.getDoctorId());
        vo.setDepartment(appt.getDepartment());
        vo.setAppointmentTime(appt.getAppointmentTime() == null ? null : appt.getAppointmentTime().format(TIME_FMT));
        vo.setScheduleId(appt.getScheduleId());
        vo.setQueueNo(appt.getQueueNo());
        vo.setSlotStart(appt.getSlotStart() == null ? null : appt.getSlotStart().format(TIME_FMT));
        vo.setStatus(appt.getStatus());
        vo.setCancelReason(appt.getCancelReason());
        vo.setRescheduledFrom(appt.getRescheduledFrom());
        vo.setCreatedAt(appt.getCreatedAt() == null ? null : appt.getCreatedAt().format(TIME_FMT));
        vo.setOrderNo(buildOrderNo(appt));
        if (doctor != null) {
            vo.setDoctorName(doctor.getRealName());
            vo.setDoctorTitle(doctor.getTitle());
            vo.setFee(feeOfTitle(doctor.getTitle()));
        } else {
            // 医生信息缺失时的兜底展示，避免页面出现空白（不伪造费用，置为 null）
            vo.setDoctorName("未知医生");
            vo.setDoctorTitle(null);
            vo.setFee(null);
        }
        return vo;
    }

    /**
     * 生成挂号单号：GH + 创建日期(yyyyMMdd) + 3 位主键补零
     *
     * @param appt 挂号实体
     * @return 形如 GH20260917008 的单号
     */
    private static String buildOrderNo(Appointment appt) {
        return buildOrderNoPublic(appt);
    }

    /**
     * 对外暴露的挂号单号生成方法（供 VisitVO 等其他模块复用同一单号规则，保证口径一致）
     *
     * @param appt 挂号实体
     * @return 形如 GH20260917008 的单号
     */
    public static String buildOrderNoPublic(Appointment appt) {
        String datePart = appt.getCreatedAt() != null
                ? appt.getCreatedAt().format(ORDER_NO_FMT)
                : (appt.getAppointmentTime() != null ? appt.getAppointmentTime().format(ORDER_NO_FMT) : "00000000");
        return "GH" + datePart + String.format("%03d", appt.getId());
    }

    /**
     * 职称对应挂号费档位（演示项目的统一计费规则，后续可迁移至挂号费配置表）
     *
     * @param title 医生职称
     * @return 挂号费（元）
     */
    public static Integer feeOfTitle(String title) {
        Map<String, Integer> feeMap = Map.of(
                "主任医师", 50,
                "副主任医师", 35,
                "主治医师", 20
        );
        return feeMap.getOrDefault(title == null ? "" : title, 20);
    }
}
