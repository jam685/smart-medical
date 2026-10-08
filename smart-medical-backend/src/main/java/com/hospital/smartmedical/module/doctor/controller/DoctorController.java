package com.hospital.smartmedical.module.doctor.controller;

import com.hospital.smartmedical.common.result.Result;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.service.DoctorService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String employeeNo = body.get("employeeNo");
        String username = body.get("username");
        String password = body.get("password");
        if ((employeeNo == null && username == null) || password == null) {
            return Result.fail(400, "请提供工号或用户名以及密码");
        }
        return Result.ok(doctorService.login(employeeNo, username, password));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        doctorService.logout();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Doctor d = doctorService.getById(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", d.getId());
        result.put("employeeNo", d.getEmployeeNo());
        result.put("realName", d.getRealName());
        result.put("gender", d.getGender());
        result.put("title", d.getTitle());
        result.put("phone", d.getPhone());
        result.put("department", d.getDepartment());
        result.put("avatar", d.getAvatar());
        result.put("bio", d.getBio());
        result.put("onlineStatus", d.getOnlineStatus());
        result.put("status", d.getStatus());
        return Result.ok(result);
    }

    @PutMapping("/me")
    public Result<Map<String, Object>> updateMe(@RequestBody Doctor updates,
                                                 HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        Doctor updated = doctorService.update(userId, updates);

        Map<String, Object> result = new HashMap<>();
        result.put("id", updated.getId());
        result.put("employeeNo", updated.getEmployeeNo());
        result.put("realName", updated.getRealName());
        result.put("gender", updated.getGender());
        result.put("title", updated.getTitle());
        result.put("phone", updated.getPhone());
        result.put("department", updated.getDepartment());
        result.put("avatar", updated.getAvatar());
        result.put("bio", updated.getBio());
        result.put("onlineStatus", updated.getOnlineStatus());
        result.put("status", updated.getStatus());
        return Result.ok(result);
    }

    /**
     * 上传当前医生头像
     * 请求类型：multipart/form-data，字段名 file
     * 返回：avatar 可访问路径
     */
    @PostMapping("/me/avatar")
    public Result<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file,
                                                    HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        String avatarUrl = doctorService.uploadAvatar(userId, file);

        Map<String, Object> result = new HashMap<>();
        result.put("avatar", avatarUrl);
        return Result.ok(result);
    }

    @GetMapping("/me/patients")
    public Result<Object> myPatients(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(doctorService.getMyPatients(userId));
    }

    @GetMapping("/me/appointments")
    public Result<Object> myAppointments(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("currentUserId");
        return Result.ok(doctorService.getMyAppointments(userId));
    }
}
