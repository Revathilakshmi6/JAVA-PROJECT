package com.risk.dupdetect.websocket;

import com.risk.dupdetect.dto.response.DuplicateRecordResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Broadcasts real-time duplicate alerts to WebSocket subscribers.
 */
@Component
@Slf4j
public class DuplicateAlertBroadcaster {

    private static final String TOPIC = "/topic/duplicate-alerts";

    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public DuplicateAlertBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Pushes a duplicate alert to all connected WebSocket clients.
     */
    public void broadcast(DuplicateRecordResponse alert) {
        log.info("Broadcasting duplicate alert: matchTier={}, id={}", alert.getMatchTier(), alert.getId());
        messagingTemplate.convertAndSend(TOPIC, alert);
    }
}
