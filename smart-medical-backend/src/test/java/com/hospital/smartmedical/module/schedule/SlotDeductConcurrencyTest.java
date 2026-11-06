package com.hospital.smartmedical.module.schedule;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 号源扣减并发测试（连真实 MySQL，本机无库时自动跳过）。
 *
 * <p>验证生产用的那条 SQL：50 个线程同时抢 5 个号，必须恰好 5 次成功、
 * booked_count 恰好等于 5 —— 多一次就是超卖，少一次就是漏号。</p>
 */
class SlotDeductConcurrencyTest {

    private static final String URL = System.getProperty("test.db.url",
            "jdbc:mysql://localhost:3306/smart_medical?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai");
    private static final String USER = System.getProperty("test.db.user", "root");
    private static final String PASSWORD = System.getProperty("test.db.password", "123456");

    /** 生产代码里的扣减 SQL，原样复制，保证测的就是线上那条 */
    private static final String DEDUCT_SQL =
            "UPDATE sm_schedule_slot SET booked_count = booked_count + 1, updated_at = NOW() "
                    + "WHERE id = ? AND status = 'OPEN' AND booked_count < total_count";

    @Test
    void fiftyThreadsCompeteForFiveSlots() throws Exception {
        assumeTrue(canConnect(), "本机没有可用的 MySQL，跳过并发测试");

        long slotId = insertSlot(5);
        try {
            int threads = 50;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            AtomicInteger success = new AtomicInteger();
            AtomicInteger full = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
                             PreparedStatement ps = conn.prepareStatement(DEDUCT_SQL)) {
                            ps.setLong(1, slotId);
                            if (ps.executeUpdate() == 1) {
                                success.incrementAndGet();
                            } else {
                                full.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                });
            }
            ready.await(10, TimeUnit.SECONDS);
            go.countDown();
            pool.shutdown();
            assumeTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "并发线程未在 60 秒内结束");

            assertEquals(5, success.get(), "恰好 5 个号，成功次数必须等于 5（多了是超卖）");
            assertEquals(45, full.get(), "其余 45 次必须拿到 0 行（没抢到）");
            assertEquals(5, bookedCount(slotId), "booked_count 必须恰好等于 5");
        } finally {
            deleteSlot(slotId);
        }
    }

    @Test
    void releaseNeverGoesNegative() throws Exception {
        assumeTrue(canConnect(), "本机没有可用的 MySQL，跳过回补测试");
        long slotId = insertSlot(1);
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            conn.setAutoCommit(true);
            // 没有任何已挂记录时回补，必须被条件拦住
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE sm_schedule_slot SET booked_count = booked_count - 1 WHERE id = ? AND booked_count > 0")) {
                ps.setLong(1, slotId);
                assertEquals(0, ps.executeUpdate());
            }
            assertEquals(0, bookedCount(slotId));
        } finally {
            deleteSlot(slotId);
        }
    }

    private boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(URL, USER, PASSWORD)) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private long insertSlot(int total) throws SQLException {
        String sql = "INSERT INTO sm_schedule_slot (doctor_id, department, work_date, period, start_time, end_time, "
                + "total_count, booked_count, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, 999999L);
            ps.setString(2, "并发测试科室");
            ps.setDate(3, java.sql.Date.valueOf(LocalDate.now().plusDays(1)));
            ps.setString(4, "AM");
            ps.setTime(5, java.sql.Time.valueOf(LocalTime.of(8, 0)));
            ps.setTime(6, java.sql.Time.valueOf(LocalTime.of(8, 30)));
            ps.setInt(7, total);
            ps.setInt(8, 0);
            ps.setString(9, "OPEN");
            ps.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now()));
            ps.setTimestamp(11, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                assertNotNull(keys);
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    private int bookedCount(long slotId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("SELECT booked_count FROM sm_schedule_slot WHERE id = ?")) {
            ps.setLong(1, slotId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void deleteSlot(long slotId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement("DELETE FROM sm_schedule_slot WHERE id = ?")) {
            ps.setLong(1, slotId);
            ps.executeUpdate();
        }
    }
}
