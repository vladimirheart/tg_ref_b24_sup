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
}
