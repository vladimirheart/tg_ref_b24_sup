package com.example.supportbot.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {

    @EmbeddedId
    private TicketId id;

    @Column(name = "group_msg_id")
    private Long groupMessageId;

    private String status;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolved_by")
    private String resolvedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id")
    private Channel channel;

    @Column(name = "reopen_count")
    private Integer reopenCount;

    @Column(name = "closed_count")
    private Integer closedCount;

    @Column(name = "work_time_total_sec")
    private Long workTimeTotalSec;

    @Column(name = "last_reopen_at")
    private OffsetDateTime lastReopenAt;

    public TicketId getId() {
        return id;
    }

    public void setId(TicketId id) {
        this.id = id;
    }

    public Long getGroupMessageId() {
        return groupMessageId;
    }

    public void setGroupMessageId(Long groupMessageId) {
        this.groupMessageId = groupMessageId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Integer getReopenCount() {
        return reopenCount;
    }

    public void setReopenCount(Integer reopenCount) {
        this.reopenCount = reopenCount;
    }

    public Integer getClosedCount() {
        return closedCount;
    }

    public void setClosedCount(Integer closedCount) {
        this.closedCount = closedCount;
    }

    public Long getWorkTimeTotalSec() {
        return workTimeTotalSec;
    }

    public void setWorkTimeTotalSec(Long workTimeTotalSec) {
        this.workTimeTotalSec = workTimeTotalSec;
    }

    public OffsetDateTime getLastReopenAt() {
        return lastReopenAt;
    }

    public void setLastReopenAt(OffsetDateTime lastReopenAt) {
        this.lastReopenAt = lastReopenAt;
    }

    public Long getUserId() {
        return id != null ? id.getUserId() : null;
    }

    public String getTicketId() {
        return id != null ? id.getTicketId() : null;
    }
}
