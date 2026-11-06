package com.hospital.smartmedical.module.patient.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.service.PatientService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    /**
     * 是否把验证码回显给调用方。
     *
     * <p>默认 false：生产必须走短信/邮件，接口只返回"已发送"。
     * 本地演示没有短信通道时，用 {@code --spring.profiles.active=demo}
     * 或 {@code app.security.expose-verification-code=true} 打开回显。</p>
     */
    @Value("${app.security.expose-verification-code:false}")
    private boolean exposeVerificationCode;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return Result.fail(400, "用户名和密码不能为空");
        }
        return Result.ok(patientService.login(username, password));
    }

    /**
     * 患者注册（无需登录）：body { username, password, realName, gender, phone, idCard }
     * 校验唯一性后入库，注册成功自动登录（返回 token + user）。
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        return Result.ok(patientService.register(
                body.get("username"), body.get("password"), body.get("realName"),
                body.get("gender"), body.get("phone"), body.get("idCard")));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        patientService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Patient patient = patientService.getById(userId);
        return Result.ok(toProfileMap(patient));
    }

    @PutMapping("/me")
    public Result<Map<String, Object>> updateMe(@RequestBody Map<String, String> body,
                                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Patient updated = patientService.update(
                userId, body.get("realName"), body.get("phone"), body.get("gender"));
        return Result.ok(toProfileMap(updated));
    }

    /**
     * 上传当前患者头像
     * 请求类型：multipart/form-data，字段名 file
     * 返回：avatar 可访问路径
     */
    @PostMapping("/me/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String avatarUrl = patientService.uploadAvatar(userId, file);

        Map<String, Object> result = new HashMap<>();
        result.put("avatar", avatarUrl);
        return Result.ok(result);
    }

    /**
     * 修改当前患者密码：body { oldPassword, newPassword }
     */
    @PutMapping("/me/password")
    public Result<Void> changePassword(@RequestBody Map<String, String> body,
                                       HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        patientService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.ok();
    }

    /**
     * 忘记密码-发送验证码（无需登录）：body { username, idCard, phone }
     * 校验用户名/身份证/手机号匹配后生成验证码。
     * 默认只返回 {sent:true}；仅演示环境（expose-verification-code=true）才把 code 一并返回。
     */
    @PostMapping("/password-reset/code")
    public Result<Map<String, Object>> sendPasswordResetCode(@RequestBody Map<String, String> body) {
        String code = patientService.sendPasswordResetCode(
                body.get("username"), body.get("idCard"), body.get("phone"));
        Map<String, Object> result = new HashMap<>();
        result.put("sent", true);
        if (exposeVerificationCode) {
            result.put("code", code);
            log.info("[密码重置] 演示环境回显验证码：username={}，code={}", body.get("username"), code);
        }
        return Result.ok(result);
    }

    /**
     * 忘记密码-重置密码（无需登录）：body { username, code, newPassword }
     * 验证码校验通过后写入新密码。
     */
    @PutMapping("/password-reset")
    public Result<Void> resetPassword(@RequestBody Map<String, String> body) {
        patientService.resetPassword(
                body.get("username"), body.get("code"), body.get("newPassword"));
        return Result.ok();
    }

    /**
     * 组装「我的」资料视图：身份证号脱敏（保留前 3 位与后 4 位），其余字段原样返回。
     */
    private Map<String, Object> toProfileMap(Patient patient) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", patient.getId());
        result.put("username", patient.getUsername());
        result.put("realName", patient.getRealName());
        result.put("gender", patient.getGender());
        result.put("phone", patient.getPhone());
        result.put("idCard", maskIdCard(patient.getIdCard()));
        result.put("avatar", patient.getAvatar());
        result.put("status", patient.getStatus());
        return result;
    }

    /**
     * 身份证号脱敏：长度不足 8 位时原样返回（数据异常也不拼接星号）。
     * 示例：110101199001011234 -> 110***********1234
     */
    private String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        int len = idCard.length();
        return idCard.substring(0, 3) + "*".repeat(len - 7) + idCard.substring(len - 4);
    }
}
