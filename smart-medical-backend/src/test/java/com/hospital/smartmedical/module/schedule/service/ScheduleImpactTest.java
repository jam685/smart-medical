package com.hospital.smartmedical.module.schedule.service;

import com.hospital.smartmedical.common.exception.BizException;
import com.hospital.smartmedical.module.appointment.entity.Appointment;
import com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper;
import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
import com.hospital.smartmedical.module.patient.entity.Patient;
import com.hospital.smartmedical.module.patient.mapper.PatientMapper;
import com.hospital.smartmedical.module.schedule.entity.ScheduleSlot;
import com.hospital.smartmedical.module.schedule.mapper.ScheduleSlotMapper;
import com.hospital.smartmedical.module.schedule.service.impl.ScheduleServiceImpl;
import com.hospital.smartmedical.module.schedule.vo.ScheduleImpactVO;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 停诊影响面与批量退号单测。
 *
 * <p>核心保证：没确认影响面就不能停诊；确认后退号数与号源回补一致；已开始的时段不能停诊。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleImpactTest {

    @Mock
    private ScheduleSlotMapper slotMapper;
    @Mock
    private DoctorMapper doctorMapper;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private com.hospital.smartmedical.module.appointment.service.AppointmentService appointmentService;
    @Mock
    private com.hospital.smartmedical.module.notification.service.NotificationService notificationService;

    private ScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ScheduleServiceImpl(slotMapper, doctorMapper, appointmentMapper, patientMapper,
                appointmentService, notificationService);
    }

    @Test
    void impactListsAffectedPatientsAndAlternatives() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ScheduleSlot s1 = slot(10L, 1, 5, tomorrow, LocalTime.of(8, 0));
        ScheduleSlot s2 = slot(11L, 0, 5, tomorrow, LocalTime.of(8, 30));
        when(slotMapper.selectBatchIds(List.of(10L, 11L))).thenReturn(List.of(s1, s2));
        when(appointmentMapper.selectList(any())).thenReturn(List.of(appointment(100L, 7L, 10L, tomorrow)));
        when(patientMapper.selectBatchIds(any())).thenReturn(List.of(patient(7L, "张三", "13800138001")));
        when(slotMapper.selectList(any())).thenReturn(List.of(
                slot(12L, 0, 5, tomorrow, LocalTime.of(9, 0))));

        ScheduleImpactVO vo = service.impact(List.of(10L, 11L));

        assertEquals(2, vo.getSlotCount());
        assertEquals(1, vo.getBookedCount());
        assertEquals(1, vo.getAffectedCount());
        assertEquals("张三", vo.getAffected().get(0).getPatientName());
        assertEquals("13800138001", vo.getAffected().get(0).getPatientPhone());
        assertTrue(vo.getAffected().get(0).getOrderNo().startsWith("GH"));
        assertEquals(1, vo.getAlternatives().size());
        assertFalse(vo.getHasStarted());
    }

    @Test
    void suspendWithoutConfirmIsRejectedAndChangesNothing() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(slotMapper.selectBatchIds(any())).thenReturn(List.of(slot(10L, 1, 5, tomorrow, LocalTime.of(8, 0))));
        when(appointmentMapper.selectList(any())).thenReturn(List.of(appointment(100L, 7L, 10L, tomorrow)));

        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatusByAdmin(List.of(10L), true, false));

        assertTrue(ex.getMessage().contains("1 位患者"));
        verify(appointmentMapper, never()).cancelIfActive(any(), anyString());
        verify(slotMapper, never()).updateById(any(ScheduleSlot.class));
    }

    @Test
    void confirmedSuspendCancelsPatientsAndResetsSeats() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(slotMapper.selectBatchIds(any())).thenReturn(List.of(slot(10L, 1, 5, tomorrow, LocalTime.of(8, 0))));
        when(appointmentMapper.selectList(any())).thenReturn(List.of(appointment(100L, 7L, 10L, tomorrow)));
        when(appointmentMapper.cancelIfActive(100L, "SUSPEND")).thenReturn(1);

        Map<String, Object> result = service.changeStatusByAdmin(List.of(10L), true, true);

        assertEquals(1, result.get("changed"));
        assertEquals(1, result.get("cancelled"));
        ArgumentCaptor<ScheduleSlot> captor = ArgumentCaptor.forClass(ScheduleSlot.class);
        verify(slotMapper).updateById(captor.capture());
        assertEquals("SUSPENDED", captor.getValue().getStatus());
        assertEquals(0, captor.getValue().getBookedCount(), "停诊后号源已约数必须归零");
    }

    @Test
    void startedSlotCannotBeSuspended() {
        ScheduleSlot past = slot(10L, 0, 5, LocalDate.now().minusDays(1), LocalTime.of(8, 0));
        when(slotMapper.selectBatchIds(any())).thenReturn(List.of(past));

        BizException ex = assertThrows(BizException.class,
                () -> service.changeStatusByAdmin(List.of(10L), true, true));

        assertTrue(ex.getMessage().contains("已开始"));
        verify(slotMapper, never()).updateById(any(ScheduleSlot.class));
    }

    @Test
    void restoreReopensSlot() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        ScheduleSlot s = slot(10L, 0, 5, tomorrow, LocalTime.of(8, 0));
        s.setStatus("SUSPENDED");
        when(slotMapper.selectBatchIds(any())).thenReturn(List.of(s));

        Map<String, Object> result = service.changeStatusByAdmin(List.of(10L), false, false);

        assertEquals(1, result.get("changed"));
        assertEquals("OPEN", s.getStatus());
        verify(slotMapper).updateById(s);
    }

    private ScheduleSlot slot(Long id, int booked, int total, LocalDate date, LocalTime start) {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(id);
        s.setDoctorId(2L);
        s.setDepartment("内分泌科");
        s.setWorkDate(date);
        s.setPeriod(start.getHour() < 12 ? "AM" : "PM");
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setTotalCount(total);
        s.setBookedCount(booked);
        s.setStatus("OPEN");
        return s;
    }

    private Appointment appointment(Long id, Long patientId, Long slotId, LocalDate date) {
        Appointment a = new Appointment();
        a.setId(id);
        a.setPatientId(patientId);
        a.setDoctorId(2L);
        a.setScheduleId(slotId);
        a.setStatus("PENDING");
        a.setAppointmentTime(LocalDateTime.of(date, LocalTime.of(8, 0)));
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    private Patient patient(Long id, String name, String phone) {
        Patient p = new Patient();
        p.setId(id);
        p.setRealName(name);
        p.setPhone(phone);
        return p;
    }
}
