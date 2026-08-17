package com.example.panel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.integration.rabbitmq")
public class IntegrationRabbitProperties {

    private String inboundExchange;
    private String inboundQueue;
    private String ticketCreatedQueue;
    private String inboundDlx;
    private String inboundDlq;
    private String ticketCreatedDlq;
    private String outboundExchange;
    private String outboundDlx;
    private String routingTelegram;
    private String routingVk;
    private String routingMax;
    private String routingTicketCreated;

    public String getInboundExchange() {
        return inboundExchange;
    }

    public void setInboundExchange(String inboundExchange) {
        this.inboundExchange = inboundExchange;
    }

    public String getInboundQueue() {
        return inboundQueue;
    }

    public void setInboundQueue(String inboundQueue) {
        this.inboundQueue = inboundQueue;
    }

    public String getTicketCreatedQueue() {
        return ticketCreatedQueue;
    }

    public void setTicketCreatedQueue(String ticketCreatedQueue) {
        this.ticketCreatedQueue = ticketCreatedQueue;
    }

    public String getInboundDlx() {
        return inboundDlx;
    }

    public void setInboundDlx(String inboundDlx) {
        this.inboundDlx = inboundDlx;
    }

    public String getInboundDlq() {
        return inboundDlq;
    }

    public void setInboundDlq(String inboundDlq) {
        this.inboundDlq = inboundDlq;
    }

    public String getTicketCreatedDlq() {
        return ticketCreatedDlq;
    }

    public void setTicketCreatedDlq(String ticketCreatedDlq) {
        this.ticketCreatedDlq = ticketCreatedDlq;
    }

    public String getOutboundExchange() {
        return outboundExchange;
    }

    public void setOutboundExchange(String outboundExchange) {
        this.outboundExchange = outboundExchange;
    }

    public String getOutboundDlx() {
        return outboundDlx;
    }

    public void setOutboundDlx(String outboundDlx) {
        this.outboundDlx = outboundDlx;
    }

    public String getRoutingTelegram() {
        return routingTelegram;
    }

    public void setRoutingTelegram(String routingTelegram) {
        this.routingTelegram = routingTelegram;
    }

    public String getRoutingVk() {
        return routingVk;
    }

    public void setRoutingVk(String routingVk) {
        this.routingVk = routingVk;
    }

    public String getRoutingMax() {
        return routingMax;
    }

    public void setRoutingMax(String routingMax) {
        this.routingMax = routingMax;
    }

    public String getRoutingTicketCreated() {
        return routingTicketCreated;
    }

    public void setRoutingTicketCreated(String routingTicketCreated) {
        this.routingTicketCreated = routingTicketCreated;
    }

    public String outboundFeedbackPromptRoutingKey(String platform, Long channelId) {
        String normalizedPlatform = platform == null || platform.isBlank()
            ? "telegram"
            : platform.trim().toLowerCase();
        long normalizedChannelId = channelId != null && channelId > 0 ? channelId : 0L;
        return "integration.outbound.feedback.prompt." + normalizedPlatform + ".channel." + normalizedChannelId;
    }
}
