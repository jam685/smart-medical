package com.hospital.smartmedical.module.ai.agent.service;

import com.hospital.smartmedical.module.ai.agent.state.MedicalGraphState;

import java.util.List;
import java.util.Optional;

/**
 * AI 会话 State 存储接口（会话隔离：sessionId 独立存储，杜绝串病情）。
 *
 * <p>实现：MemoryAiStateStore（内存，默认）/ RedisAiStateStore（Redis 持久化，ai.state-store=redis）。</p>
 */
public interface AiStateStore {

    /**
     * 读取会话状态。
     *
     * @param sessionId 会话 ID
     * @return 状态（不存在返回 empty）
     */
    Optional<MedicalGraphState> get(String sessionId);

    /**
     * 保存/覆盖会话状态。
     *
     * @param sessionId 会话 ID
     * @param state     状态
     */
    void save(String sessionId, MedicalGraphState state);

    /**
     * 删除会话状态。
     *
     * @param sessionId 会话 ID
     */
    void delete(String sessionId);

    /**
     * 按患者列出会话（前端历史会话列表）。
     *
     * @param patientId 患者 ID
     * @return 该患者全部会话状态（按最后活动时间倒序）
     */
    List<MedicalGraphState> listByPatient(Long patientId);
}
