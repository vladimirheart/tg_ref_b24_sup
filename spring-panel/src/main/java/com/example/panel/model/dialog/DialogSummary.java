package com.example.panel.model.dialog;

import java.util.List;

public record DialogSummary(long totalTickets,
                            long resolvedTickets,
                            long pendingTickets,
                            List<DialogChannelStat> channelStats) {
}