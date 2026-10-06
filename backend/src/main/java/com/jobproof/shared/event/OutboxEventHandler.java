package com.jobproof.shared.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 外盒事件增量消费者。资料库或岗位确认变化通过它使相关派生结果失效。
 */
public interface OutboxEventHandler {

    boolean supports(String eventType);

    void handle(String eventType, JsonNode payload);
}
