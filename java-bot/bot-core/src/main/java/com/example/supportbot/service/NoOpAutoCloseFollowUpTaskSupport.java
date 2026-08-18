package com.example.supportbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.integration.transport.mode", havingValue = "rabbitmq")
public class NoOpAutoCloseFollowUpTaskSupport implements AutoCloseFollowUpTaskSupport {

    private static final Logger log = LoggerFactory.getLogger(NoOpAutoCloseFollowUpTaskSupport.class);

    @Override
    public void createTaskForAutoClosedDialog(String ticketId) {
        log.debug("Skipping bot-side auto-close follow-up task creation because RabbitMQ transport delegates panel-side task handling");
    }
}
