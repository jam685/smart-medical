package com.hospital.smartmedical.module.appointment;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.appointment.service.impl.AppointmentServiceImpl;
import com.hospital.smartmedical.module.doctor.entity.Doctor;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.mapper.ScheduleSlotMapper;
import com.hospital.smartmedical.module.visit.entity.Visit;
import com.hospital.smartmedical.module.visit.mapper.VisitMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 号源挂号链路单测：扣号、号满、取消回补、重复取消。
 *
 * <p>并发正确性由 {@code SlotDeductConcurrencyTest} 连真实数据库验证；
 * 这里验证编排逻辑——尤其是"扣号失败不落单""取消只回补一次"。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentSlotBookingTest {

    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private DoctorMapper doctorMapper;
    @Mock
    private VisitMapper visitMapper;
    @Mock
    private ScheduleSlotMapper scheduleSlotMapper;
    @Mock
    private PatientMapper patientMapper;

    private AppointmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AppointmentServiceImpl(appointmentMapper, doctorMapper, visitMapper,
                scheduleSlotMapper, patientMapper);
    }

    @Test
    void bookFromSlotDeductsAndAssignsQueueNo() {
        ScheduleSlot before = slot(10L, 0, 5);
        ScheduleSlot after = slot(10L, 1, 5);
        when(patientMapper.lockById(1L)).thenReturn(1L);
        when(scheduleSlotMapper.selectById(10L)).thenReturn(before, after);
        when(scheduleSlotMapper.deduct(10L)).thenReturn(1);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(doctorMapper.selectById(2L)).thenReturn(doctor());
        when(appointmentMapper.insert(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });

        Appointment appt = service.createAppointmentBySlot(1L, Role.PATIENT, 10L);

        assertEquals(10L, appt.getScheduleId());
        assertEquals(1, appt.getQueueNo(), "扣号后 booked_count=1，序号应为 1");
        assertEquals("PENDING", appt.getStatus());
        assertEquals(before.getWorkDate().atTime(before.getStartTime()), appt.getAppointmentTime());
        verify(scheduleSlotMapper).deduct(10L);
        verify(visitMapper).insert(any(Visit.class));
    }

    @Test
    void fullSlotThrowsAndWritesNothing() {
        when(patientMapper.lockById(1L)).thenReturn(1L);
        when(scheduleSlotMapper.selectById(10L)).thenReturn(slot(10L, 5, 5));
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(scheduleSlotMapper.deduct(10L)).thenReturn(0);

        BizException ex = assertThrows(BizException.class,
                () -> service.createAppointmentBySlot(1L, Role.PATIENT, 10L));

        assertEquals("该时段号源已约满，请选择其他时段", ex.getMessage());
        verify(appointmentMapper, never()).insert(any(Appointment.class));
        verify(visitMapper, never()).insert(any(Visit.class));
    }

    @Test
    void pastSlotIsRejectedBeforeDeduct() {
        ScheduleSlot past = slot(10L, 0, 5);
        past.setWorkDate(LocalDate.now().minusDays(1));
        when(patientMapper.lockById(1L)).thenReturn(1L);
        when(scheduleSlotMapper.selectById(10L)).thenReturn(past);

        assertThrows(BizException.class, () -> service.createAppointmentBySlot(1L, Role.PATIENT, 10L));
        verify(scheduleSlotMapper, never()).deduct(anyLong());
    }

    @Test
    void cancelReleasesTheSlot() {
        Appointment appt = appointment(5L, 1L, 10L, "PENDING");
        when(appointmentMapper.selectById(5L)).thenReturn(appt);
        when(visitMapper.selectOne(any())).thenReturn(null);
        when(appointmentMapper.cancelIfActive(5L, "CANCEL")).thenReturn(1);

        service.cancelAppointment(5L, 1L, Role.PATIENT);

        verify(scheduleSlotMapper).release(10L);
    }

    @Test
    void secondCancelDoesNotReleaseAgain() {
        Appointment appt = appointment(5L, 1L, 10L, "PENDING");
        when(appointmentMapper.selectById(5L)).thenReturn(appt);
        when(visitMapper.selectOne(any())).thenReturn(null);
        // 条件更新返回 0：说明已被别的请求取消，本次不能再回补号源
        when(appointmentMapper.cancelIfActive(5L, "CANCEL")).thenReturn(0);

        assertThrows(BizException.class, () -> service.cancelAppointment(5L, 1L, Role.PATIENT));
        verify(scheduleSlotMapper, never()).release(anyLong());
    }

    /** 构造未来时间的号源 */
    private ScheduleSlot slot(Long id, int booked, int total) {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(id);
        s.setDoctorId(2L);
        s.setDepartment("内分泌科");
        s.setWorkDate(LocalDate.now().plusDays(1));
        s.setPeriod("AM");
        s.setStartTime(LocalTime.of(8, 0));
        s.setEndTime(LocalTime.of(8, 30));
        s.setTotalCount(total);
        s.setBookedCount(booked);
        s.setStatus("OPEN");
        return s;
    }

    private Doctor doctor() {
        Doctor d = new Doctor();
        d.setId(2L);
        d.setRealName("李医生");
        d.setDepartment("内分泌科");
        return d;
    }

    private Appointment appointment(Long id, Long patientId, Long slotId, String status) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatientId(patientId);
        a.setDoctorId(2L);
        a.setScheduleId(slotId);
        a.setQueueNo(1);
        a.setStatus(status);
        a.setSlotStart(LocalDateTime.now().plusDays(1));
        a.setAppointmentTime(LocalDateTime.now().plusDays(1));
        return a;
    }
}
