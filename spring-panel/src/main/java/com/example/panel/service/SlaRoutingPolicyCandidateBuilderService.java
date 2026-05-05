package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SlaRoutingPolicyCandidateBuilderService {

    public Map<String, Object> buildCandidate(DialogListItem dialog, Long minutesLeft, String currentResponsible) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("ticket_id", dialog.ticketId());
        candidate.put("request_number", dialog.requestNumber());
        candidate.put("client", dialog.displayClientName());
        candidate.put("minutes_left", minutesLeft);
        candidate.put("status", dialog.statusLabel());
        candidate.put("channel", dialog.channelLabel());
        candidate.put("business", dialog.businessLabel());
        candidate.put("location", dialog.location());
        candidate.put("categories", dialog.categories());
        candidate.put("client_status", dialog.clientStatus());
        candidate.put("responsible", currentResponsible);
        candidate.put("unread_count", dialog.unreadCount());
        candidate.put("rating", dialog.rating());
        candidate.put("sla_state", minutesLeft != null && minutesLeft < 0 ? "breached" : "at_risk");
        candidate.put("escalation_scope", currentResponsible == null ? "unassigned" : "assigned");
        return candidate;
    }
}
