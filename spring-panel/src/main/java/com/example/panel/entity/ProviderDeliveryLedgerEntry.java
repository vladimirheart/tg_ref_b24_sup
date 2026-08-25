package com.example.panel.entity;

import com.example.panel.converter.LenientOffsetDateTimeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "provider_delivery_ledger")
@Getter
@Setter
public class ProviderDeliveryLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "ticket_id")
    private String ticketId;

    @Column(name = "platform", nullable = false)
    private String platform;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "sender_kind", nullable = false)
    private String senderKind;

    @Column(name = "message_kind", nullable = false)
    private String messageKind;

    @Column(name = "delivery_status", nullable = false)
    private String deliveryStatus;

    @Column(name = "classification", nullable = false)
    private String classification;

    @Column(name = "severity_level", nullable = false)
    private String severityLevel;

    @Column(name = "retry_state", nullable = false)
    private String retryState;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "provider_error_code")
    private String providerErrorCode;

    @Column(name = "provider_message")
    private String providerMessage;

    @Column(name = "response_excerpt")
    private String responseExcerpt;

    @Column(name = "provider_message_id")
    private Long providerMessageId;

    @Column(name = "reply_to_message_id")
    private Long replyToMessageId;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "attempted_at", nullable = false)
    @Convert(converter = LenientOffsetDateTimeConverter.class)
    private OffsetDateTime attemptedAt;
}
