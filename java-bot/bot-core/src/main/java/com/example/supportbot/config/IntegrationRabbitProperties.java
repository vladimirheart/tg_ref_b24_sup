package com.example.supportbot.config;

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
    private String outboundQueue;
    private String outboundDlq;
    private String outboundRoutingKey;
    private String routingTelegram;
    private String routingVk;
    private String routingMax;
    private String routingTicketCreated;
    private Integer outboundConcurrency = 1;
    private Integer outboundMaxConcurrency = 4;
    private Integer outboundPrefetch = 10;

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

    public String getInboundDlx() {
        return inboundDlx;
    }

    public void setInboundDlx(String inboundDlx) {
        this.inboundDlx = inboundDlx;
    }

    public String getTicketCreatedQueue() {
        return ticketCreatedQueue;
    }

    public void setTicketCreatedQueue(String ticketCreatedQueue) {
        this.ticketCreatedQueue = ticketCreatedQueue;
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

    public String getOutboundQueue() {
        return outboundQueue;
    }

    public void setOutboundQueue(String outboundQueue) {
        this.outboundQueue = outboundQueue;
    }

    public String getOutboundDlq() {
        return outboundDlq;
    }

    public void setOutboundDlq(String outboundDlq) {
        this.outboundDlq = outboundDlq;
    }

    public String getOutboundRoutingKey() {
        return outboundRoutingKey;
    }

    public void setOutboundRoutingKey(String outboundRoutingKey) {
        this.outboundRoutingKey = outboundRoutingKey;
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

    public Integer getOutboundConcurrency() {
        return outboundConcurrency;
    }

    public void setOutboundConcurrency(Integer outboundConcurrency) {
        this.outboundConcurrency = outboundConcurrency;
    }

    public Integer getOutboundMaxConcurrency() {
        return outboundMaxConcurrency;
    }

    public void setOutboundMaxConcurrency(Integer outboundMaxConcurrency) {
        this.outboundMaxConcurrency = outboundMaxConcurrency;
    }

    public Integer getOutboundPrefetch() {
        return outboundPrefetch;
    }

    public void setOutboundPrefetch(Integer outboundPrefetch) {
        this.outboundPrefetch = outboundPrefetch;
    }

    public String routingKeyForPlatform(String platform) {
        if (platform == null) {
            return routingTelegram;
        }
        return switch (platform.trim().toLowerCase()) {
            case "vk" -> routingVk;
            case "max" -> routingMax;
            default -> routingTelegram;
        };
    }
}
