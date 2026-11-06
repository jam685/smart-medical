package com.hospital.smartmedical.module.appointment.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.result.PageResult;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.vo.AppointmentVO;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 挂号业务服务接口
 * 定义可约医生查询、挂号创建、挂号分页查询、详情、取消五个业务能力。
 */
public interface AppointmentService {

    /**
     * 查询可挂号医生（onlineStatus=ONLINE），可选科室/关键词过滤
     *
     * @param department 科室名（可空，空则不过滤科室）
     * @param keyword    医生姓名/工号关键词（可空）
     * @return 医生信息 Map 列表（含 id、姓名、科室、职称、在线状态、挂号费）
     */
    List<Map<String, Object>> listAvailableDoctors(String department, String keyword);

    /**
     * 批量查询可挂号医生（按科室集合一次查询，避免逐个科室循环查询造成 N+1）
     *
     * @param departments 科室名集合（可空/空集合，空则查询全部在线医生）
     * @return 医生信息 Map 列表（含 id、姓名、科室、职称、在线状态、挂号费）
     */
    List<Map<String, Object>> listAvailableDoctorsByDepts(Collection<String> departments);

    /**
     * 校验是否已存在同一患者同一医生同一天的有效挂号（重复挂号拦截）。
     *
     * @param patientId 患者 ID
     * @param doctorId  医生 ID
     * @param date      就诊日期
     * @return true=已存在有效挂号（状态非 CANCELLED），false=可正常挂号
     */
    boolean hasAppointmentOnDate(Long patientId, Long doctorId, LocalDate date);

    /**
     * 创建挂号，同时自动创建就诊进度 Visit
     *
     * @param userId          当前登录用户 ID（患者端即 sm_patient.id）
     * @param role            当前登录用户角色（仅 PATIENT 允许）
     * @param doctorId        选择的医生 ID
     * @param department      科室名（空则取医生所属科室）
     * @param appointmentTime 患者选择的就诊时间（不可为空）
     * @return 新建成功的挂号实体（含自增主键）
     */
    Appointment createAppointment(Long userId, Role role, Long doctorId, String department, LocalDateTime appointmentTime);

    /**
     * 按号源下单（患者端选号页与 AI 挂号统一走这里）。
     *
     * <p>扣号用一条带条件的 UPDATE 完成，失败即号满；扣号、建单、建就诊进度在同一事务内，
     * 任一步失败整体回滚，不会出现"号扣了单没生成"。</p>
     *
     * @param userId 当前登录患者 ID
     * @param role   当前角色（仅 PATIENT）
     * @param slotId 号源 ID（sm_schedule_slot.id）
     * @return 新建挂号单（含 queueNo 序号）
     */
    Appointment createAppointmentBySlot(Long userId, Role role, Long slotId);

    /**
     * 分页查询挂号列表（患者仅看本人、医生仅看本人接诊、管理员看全部）
     *
     * @param userId 当前登录用户 ID
     * @param role   当前登录用户角色
     * @param page   页码（从 1 开始）
     * @param size   每页条数
     * @param status 状态过滤（可空；PENDING/CONFIRMED/FINISHED/CANCELLED）
     * @return 挂号 VO 分页结果（含医生姓名、职称、单号、挂号费）
     */
    PageResult<AppointmentVO> listAppointments(Long userId, Role role, int page, int size, String status);

    /**
     * 查询挂号详情（含归属权限校验）
     *
     * @param id     挂号单 ID
     * @param userId 当前登录用户 ID
     * @param role   当前登录用户角色
     * @return 挂号 VO
     */
    AppointmentVO getAppointmentDetail(Long id, Long userId, Role role);

    /**
     * 患者自助改约：同医生换时段，原子完成"退旧号 → 扣新号 → 建新单"。
     *
     * <p>不允许换医生（涉及费用差异，走取消重挂）；已叫号/已开始的单不能改约。
     * 新单记录 rescheduled_from，旧单 cancel_reason=RESCHEDULE，形成可追溯链路。</p>
     *
     * @param id       原挂号单 ID
     * @param userId   当前患者 ID
     * @param role     当前角色（仅 PATIENT）
     * @param toSlotId 目标号源 ID（必须属于同一医生）
     * @return 新挂号单
     */
    Appointment rescheduleAppointment(Long id, Long userId, Role role, Long toSlotId);

    /**
     * 取消挂号（业务规则：仅患者本人、且单据未取消可操作，状态置为 CANCELLED）
     *
     * @param id     挂号单 ID
     * @param userId 当前登录用户 ID
     * @param role   当前登录用户角色
     */
    void cancelAppointment(Long id, Long userId, Role role);
}
