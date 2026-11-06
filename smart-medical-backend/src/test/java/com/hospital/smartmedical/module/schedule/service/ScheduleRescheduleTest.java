package com.hospital.smartmedical.module.schedule.service;

import com.hospital.smartmedical.common.enums.Role;
import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.appointment.service.AppointmentService;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.mapper.ScheduleSlotMapper;
import com.hospital.smartmedical.module.schedule.service.impl.ScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一键改约单测：批量迁移 + 目标容量校验 + 源时段乐观守卫。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleRescheduleTest {

    @Mock
    private ScheduleSlotMapper slotMapper;
    @Mock
    private DoctorMapper doctorMapper;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private AppointmentService appointmentService;
    @Mock
    private com.hospital.smartmedical.module.notification.service.NotificationService notificationService;

    private ScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScheduleServiceImpl(slotMapper, doctorMapper, appointmentMapper,
                patientMapper, appointmentService, notificationService);
    }

    @Test
    void rescheduleMovesAllPatientsAndSuspendsSource() {
        ScheduleSlot from = slot(10L, 2, 5, LocalTime.of(8, 0));
        ScheduleSlot to = slot(20L, 0, 5, LocalTime.of(10, 0));
        when(slotMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(from));
        when(slotMapper.selectById(20L)).thenReturn(to);
        when(appointmentMapper.selectList(any())).thenReturn(List.of(
                appointment(100L, 7L, 10L), appointment(101L, 8L, 10L)));
        when(appointmentMapper.cancelIfActive(100L, "RESCHEDULE")).thenReturn(1);
        when(appointmentMapper.cancelIfActive(101L, "RESCHEDULE")).thenReturn(1);
        when(slotMapper.release(10L)).thenReturn(1);
        when(slotMapper.suspendIfEmpty(10L)).thenReturn(1);
        when(appointmentService.createAppointmentBySlot(anyLong(), any(Role.class), eq(20L)))
                .thenReturn(new Appointment());

        Map<String, Object> result = service.reschedule(List.of(10L), List.of(20L), null);

        assertEquals(2, result.get("rescheduled"));
        assertEquals(1, result.get("suspended"));
        assertEquals(List.of(20L), result.get("toSlotIds"));
        verify(appointmentService, times(2)).createAppointmentBySlot(anyLong(), eq(Role.PATIENT), eq(20L));
        verify(slotMapper, times(2)).release(10L);
        verify(slotMapper).suspendIfEmpty(10L);
    }

    @Test
    void rejectsWhenTargetHasNotEnoughSeats() {
        ScheduleSlot from = slot(10L, 2, 5, LocalTime.of(8, 0));
        ScheduleSlot to = slot(20L, 4, 5, LocalTime.of(10, 0));
        when(slotMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(from));
        when(slotMapper.selectById(20L)).thenReturn(to);
        when(appointmentMapper.selectList(any())).thenReturn(List.of(
                appointment(100L, 7L, 10L), appointment(101L, 8L, 10L)));

        BizException ex = assertThrows(BizException.class, () -> service.reschedule(List.of(10L), List.of(20L), null));

        assertTrue(ex.getMessage().contains("余号不足"));
        verify(appointmentMapper, never()).cancelIfActive(anyLong(), any());
        verify(appointmentService, never()).createAppointmentBySlot(anyLong(), any(), anyLong());
    }

    @Test
    void rollsBackWhenSourceGotBookedConcurrently() {
        ScheduleSlot from = slot(10L, 1, 5, LocalTime.of(8, 0));
        ScheduleSlot to = slot(20L, 0, 5, LocalTime.of(10, 0));
        when(slotMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(from));
        when(slotMapper.selectById(20L)).thenReturn(to);
        when(appointmentMapper.selectList(any())).thenReturn(List.of(appointment(100L, 7L, 10L)));
        when(appointmentMapper.cancelIfActive(100L, "RESCHEDULE")).thenReturn(1);
        when(appointmentService.createAppointmentBySlot(anyLong(), any(), eq(20L))).thenReturn(new Appointment());
        // 源时段在迁移后又被别人抢到号 → 守卫失败
        when(slotMapper.suspendIfEmpty(10L)).thenReturn(0);

        BizException ex = assertThrows(BizException.class, () -> service.reschedule(List.of(10L), List.of(20L), null));

        assertTrue(ex.getMessage().contains("出现新的预约"));
    }

    @Test
    void splitsPatientsAcrossTargetsWhenOneSlotIsNotEnough() {
        ScheduleSlot from = slot(10L, 2, 5, LocalTime.of(8, 0));
        ScheduleSlot t1 = slot(20L, 1, 2, LocalTime.of(9, 0));   // 余 1
        ScheduleSlot t2 = slot(21L, 0, 5, LocalTime.of(10, 0));  // 余 5
        when(slotMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(from));
        when(slotMapper.selectById(20L)).thenReturn(t1);
        when(slotMapper.selectById(21L)).thenReturn(t2);
        when(appointmentMapper.selectList(any())).thenReturn(List.of(
                appointment(100L, 7L, 10L), appointment(101L, 8L, 10L)));
        when(appointmentMapper.cancelIfActive(100L, "RESCHEDULE")).thenReturn(1);
        when(appointmentMapper.cancelIfActive(101L, "RESCHEDULE")).thenReturn(1);
        when(slotMapper.release(10L)).thenReturn(1);
        when(slotMapper.suspendIfEmpty(10L)).thenReturn(1);
        when(appointmentService.createAppointmentBySlot(anyLong(), any(Role.class), anyLong()))
                .thenReturn(new Appointment());

        Map<String, Object> result = service.reschedule(List.of(10L), List.of(20L, 21L), null);

        assertEquals(2, result.get("rescheduled"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        assertEquals(2, assignments.size());
        assertEquals(20L, assignments.get(0).get("toSlotId"), "第一个患者先填余号少的时段");
        assertEquals(21L, assignments.get(1).get("toSlotId"), "第二个患者顺延到下一个时段");
    }

    @Test
    void rejectsTargetInFromSlots() {
        when(slotMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(slot(10L, 0, 5, LocalTime.of(8, 0))));
        assertThrows(BizException.class, () -> service.reschedule(List.of(10L), List.of(10L), null));
    }

    private ScheduleSlot slot(Long id, int booked, int total, LocalTime start) {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(id);
        s.setDoctorId(2L);
        s.setDepartment("内分泌科");
        s.setWorkDate(LocalDate.now().plusDays(1));
        s.setPeriod(start.getHour() < 12 ? "AM" : "PM");
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setTotalCount(total);
        s.setBookedCount(booked);
        s.setStatus("OPEN");
        return s;
    }

    private Appointment appointment(Long id, Long patientId, Long slotId) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatientId(patientId);
        a.setDoctorId(2L);
        a.setScheduleId(slotId);
        a.setStatus("PENDING");
        a.setAppointmentTime(LocalDate.now().plusDays(1).atTime(8, 0));
        return a;
    }
}
