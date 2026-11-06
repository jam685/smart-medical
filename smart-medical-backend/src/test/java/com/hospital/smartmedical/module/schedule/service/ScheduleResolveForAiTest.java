package com.hospital.smartmedical.module.schedule.service;

import com.hospital.smartmedical.module.doctor.mapper.DoctorMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * AI 挂号时间解析单测：把"明天上午9点"落到真正可约的号源。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScheduleResolveForAiTest {

    @Mock
    private ScheduleSlotMapper slotMapper;
    @Mock
    private DoctorMapper doctorMapper;
    @Mock
    private com.hospital.smartmedical.module.appointment.mapper.AppointmentMapper appointmentMapper;
    @Mock
    private com.hospital.smartmedical.module.patient.mapper.PatientMapper patientMapper;
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
    void prefersSameDaySlotAtOrAfterDesiredTime() {
        LocalDate day = LocalDate.now().plusDays(1);
        List<ScheduleSlot> candidates = List.of(
                slot(day, LocalTime.of(8, 0)),
                slot(day, LocalTime.of(9, 0)),
                slot(day, LocalTime.of(9, 30)));
        when(slotMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(candidates));

        ScheduleSlot picked = service.resolveForAi(2L, day.atTime(9, 0));

        assertEquals(LocalTime.of(9, 0), picked.getStartTime(), "9 点的号应命中 09:00 时段而不是 09:30");
    }

    @Test
    void fallsBackToNextAvailableDayWhenRequestedDayHasNoSlot() {
        LocalDate nextDay = LocalDate.now().plusDays(2);
        when(slotMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(
                slot(nextDay, LocalTime.of(8, 0)))));

        ScheduleSlot picked = service.resolveForAi(2L, LocalDate.now().plusDays(1).atTime(9, 0));

        assertEquals(nextDay, picked.getWorkDate(), "当天无号时顺延到最近有号的日期");
    }

    @Test
    void returnsNullWhenNoSlotInSevenDays() {
        when(slotMapper.selectList(any())).thenReturn(new java.util.ArrayList<>());
        assertNull(service.resolveForAi(2L, LocalDate.now().plusDays(1).atTime(9, 0)));
    }

    private ScheduleSlot slot(LocalDate day, LocalTime start) {
        ScheduleSlot s = new ScheduleSlot();
        s.setId(start.toSecondOfDay() * 1L);
        s.setDoctorId(2L);
        s.setDepartment("内分泌科");
        s.setWorkDate(day);
        s.setPeriod(start.getHour() < 12 ? "AM" : "PM");
        s.setStartTime(start);
        s.setEndTime(start.plusMinutes(30));
        s.setTotalCount(5);
        s.setBookedCount(0);
        s.setStatus("OPEN");
        return s;
    }
}
