package com.hospital.smartmedical.module.onlineconsult.vo;

import com.hospital.smartmedical.module.doctor.entity.Doctor;
import lombok.Data;

import java.io.Serializable;

/**
 * 在线医生视图对象（VO）。
 *
 * <p>用于「查看在线医生列表（OC-12）」与「查看问诊详情（OC-03）」返回医生公开信息，
 * 刻意剔除 password 等敏感字段，避免接口泄露医生密码哈希。</p>
 */
@Data
public class OnlineDoctorVO implements Serializable {

    /** 医生主键 */
    private Long id;

    /** 工号 */
    private String employeeNo;

    /** 姓名 */
    private String realName;

    /** 性别：男 / 女 */
    private String gender;

    /** 职称：主任医师 / 副主任医师 / 主治医师 */
    private String title;

    /** 手机号 */
    private String phone;

    /** 科室 */
    private String department;

    /** 头像 URL（空则前端用姓名首字母占位） */
    private String avatar;

    /** 医生简介 / 擅长领域 / 成就 */
    private String bio;

    /** 在线状态：ONLINE / OFFLINE */
    private String onlineStatus;

    /**
     * 是否已接诊（仅「我的挂号主治医生」接口返回该字段）。
     *
     * <p>true = 医生已接诊，可发起在线问诊；false = 已挂号但医生尚未接诊，暂不能发起。
     * 其它接口该字段为 null，前端可按需忽略。</p>
     */
    private Boolean accepted;

    /**
     * 由 Doctor 实体构造安全的视图对象，剔除密码字段。
     *
     * @param d 医生实体
     * @return 不含敏感字段的医生视图
     */
    public static OnlineDoctorVO from(Doctor d) {
        OnlineDoctorVO vo = new OnlineDoctorVO();
        vo.setId(d.getId());
        vo.setEmployeeNo(d.getEmployeeNo());
        vo.setRealName(d.getRealName());
        vo.setGender(d.getGender());
        vo.setTitle(d.getTitle());
        vo.setPhone(d.getPhone());
        vo.setDepartment(d.getDepartment());
        vo.setAvatar(d.getAvatar());
        vo.setBio(d.getBio());
        vo.setOnlineStatus(d.getOnlineStatus());
        return vo;
    }
}
