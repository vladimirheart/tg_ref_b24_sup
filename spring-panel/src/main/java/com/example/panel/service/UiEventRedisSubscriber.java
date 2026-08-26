package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RuntimeWorkload(
    id = "ui-event-redis-subscriber",
    roles = {RuntimeRole.WEB},
    replicaPolicy = RuntimeReplicaPolicy.PROCESS_LOCAL
)
public class UiEventRedisSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(UiEventRedisSubscriber.class);

    private final ObjectMapper objectMapper;
    private final UiEventStreamService uiEventStreamService;

    public UiEventRedisSubscriber(ObjectMapper objectMapper,
                                  UiEventStreamService uiEventStreamService) {
        this.objectMapper = objectMapper;
        this.uiEventStreamService = uiEventStreamService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        if (message == null || message.getBody() == null || message.getBody().length == 0) {
            return;
        }

        String serialized = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            UiEventFanoutEnvelope envelope = objectMapper.readValue(
                serialized,
                UiEventFanoutEnvelope.class
            );
            if (!StringUtils.hasText(envelope.eventName())) {
                return;
            }
            uiEventStreamService.deliverFanoutEvent(envelope);
        } catch (Exception ex) {
            log.warn("Ignoring invalid distributed UI event payload: {}", ex.getMessage());
        }
    }
}
