package com.example.panel.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.OffsetDateTime;

@Entity
@Table(name = "chat_history")
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String sender;

    private String message;

    private OffsetDateTime timestamp;

    private String ticketId;

    private String messageType;

    private String attachment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    private Long tgMessageId;

    private Long replyToTgId;

    @Column(name = "original_message")
    private String originalMessage;

    @Column(name = "forwarded_from")
    private String forwardedFrom;

    @Column(name = "file_name")
    private String fileName;

    private OffsetDateTime editedAt;

    private OffsetDateTime deletedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getAttachment() {
        return attachment;
    }

    public void setAttachment(String attachment) {
        this.attachment = attachment;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Long getTgMessageId() {
        return tgMessageId;
    }

    public void setTgMessageId(Long tgMessageId) {
        this.tgMessageId = tgMessageId;
    }

    public Long getReplyToTgId() {
        return replyToTgId;
    }

    public void setReplyToTgId(Long replyToTgId) {
        this.replyToTgId = replyToTgId;
    }

    public OffsetDateTime getEditedAt() {
        return editedAt;
    }

    public void setEditedAt(OffsetDateTime editedAt) {
        this.editedAt = editedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public String getForwardedFrom() {
        return forwardedFrom;
    }

    public void setForwardedFrom(String forwardedFrom) {
        this.forwardedFrom = forwardedFrom;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
