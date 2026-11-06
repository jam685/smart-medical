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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 患者自助改约单测：同医生换号、原子换号、链路留痕与各类拦截。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AppointmentRescheduleTest {

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
    void rescheduleSwapsSlotAndKeepsTraceability() {
        Appointment old = appointment(50L, 1L, 10L, "PENDING", LocalTime.of(9, 0));
        ScheduleSlot target = slot(20L, 2L, LocalTime.of(10, 0), 0, 5);
        ScheduleSlot afterDeduct = slot(20L, 2L, LocalTime.of(10, 0), 1, 5);
        when(appointmentMapper.selectById(50L)).thenReturn(old);
        when(scheduleSlotMapper.selectById(20L)).thenReturn(target, afterDeduct);
        when(visitMapper.selectOne(any())).thenReturn(null);
        when(appointmentMapper.cancelIfActive(50L, "RESCHEDULE")).thenReturn(1);
        when(patientMapper.lockById(1L)).thenReturn(1L);
        when(appointmentMapper.selectCount(any())).thenReturn(0L);
        when(scheduleSlotMapper.deduct(20L)).thenReturn(1);
        when(doctorMapper.selectById(2L)).thenReturn(doctor());
        when(appointmentMapper.insert(any(Appointment.class))).thenAnswer(inv -> {
            Appointment a = inv.getArgument(0);
            a.setId(99L);
            return 1;
        });

        Appointment created = service.rescheduleAppointment(50L, 1L, Role.PATIENT, 20L);

        assertEquals(99L, created.getId());
        assertEquals(50L, created.getRescheduledFrom(), "新单必须记录改约来源");
        assertEquals(20L, created.getScheduleId());
        assertEquals("PENDING", created.getStatus());
        verify(scheduleSlotMapper).release(10L);
        verify(appointmentMapper).updateById(created);
    }

    @Test
    void rejectsOtherDoctorsSlot() {
        when(appointmentMapper.selectById(50L)).thenReturn(appointment(50L, 1L, 10L, "PENDING", LocalTime.of(9, 0)));
        when(scheduleSlotMapper.selectById(21L)).thenReturn(slot(21L, 3L, LocalTime.of(10, 0), 0, 5));

        BizException ex = assertThrows(BizException.class,
                () -> service.rescheduleAppointment(50L, 1L, Role.PATIENT, 21L));

        assertTrue(ex.getMessage().contains("同一医生"));
        verify(appointmentMapper, never()).cancelIfActive(anyLong(), any());
    }

    @Test
    void rejectsSameSlot() {
        Appointment old = appointment(50L, 1L, 10L, "PENDING", LocalTime.of(9, 0));
        when(appointmentMapper.selectById(50L)).thenReturn(old);

        BizException ex = assertThrows(BizException.class,
                () -> service.rescheduleAppointment(50L, 1L, Role.PATIENT, 10L));

        assertTrue(ex.getMessage().contains("相同"));
    }

    @Test
    void rejectsWhenDoctorAlreadyCalled() {
        when(appointmentMapper.selectById(50L)).thenReturn(appointment(50L, 1L, 10L, "PENDING", LocalTime.of(9, 0)));
        when(scheduleSlotMapper.selectById(20L)).thenReturn(slot(20L, 2L, LocalTime.of(10, 0), 0, 5));
        Visit called = new Visit();
        called.setStatus("CALLING");
        when(visitMapper.selectOne(any())).thenReturn(called);

        BizException ex = assertThrows(BizException.class,
                () -> service.rescheduleAppointment(50L, 1L, Role.PATIENT, 20L));

        assertTrue(ex.getMessage().contains("已叫号"));
        verify(appointmentMapper, never()).cancelIfActive(anyLong(), any());
    }

    @Test
    void rejectsWhenNotOwner() {
        when(appointmentMapper.selectById(50L)).thenReturn(appointment(50L, 1L, 10L, "PENDING", LocalTime.of(9, 0)));

        assertThrows(BizException.class,
                () -> service.rescheduleAppointment(50L, 2L, Role.PATIENT, 20L));
    }

    private Appointment appointment(Long id, Long patientId, Long slotId, String status, LocalTime start) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatientId(patientId);
        a.setDoctorId(2L);
        a.setScheduleId(slotId);
        a.setStatus(status);
        a.setAppointmentTime(LocalDate.now().plusDays(1).atTime(start));
        a.setSlotStart(LocalDate.now().plusDays(1).atTime(start));
        return a;
    }

    private ScheduleSlot slot(Long id, Long doctorId, LocalTime start, int booked, int total) {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(id);
        s.setDoctorId(doctorId);
        s.setDepartment("心内科");
        s.setWorkDate(LocalDate.now().plusDays(1));
        s.setPeriod(start.getHour() < 12 ? "AM" : "PM");
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setTotalCount(total);
        s.setBookedCount(booked);
        s.setStatus("OPEN");
        return s;
    }

    private Doctor doctor() {
        Doctor d = new Doctor();
        d.setId(2L);
        d.setRealName("王医生");
        d.setDepartment("心内科");
        return d;
    }
}
