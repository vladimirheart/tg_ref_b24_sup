package com.example.panel.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class SlaRoutingRuleCandidateContextService {

    private final SlaRoutingRuleMatchNormalizerService matchNormalizerService;
    private final SlaRoutingRuleScalarParserService scalarParserService;

    @Autowired
    public SlaRoutingRuleCandidateContextService(SlaRoutingRuleMatchNormalizerService matchNormalizerService,
                                                 SlaRoutingRuleScalarParserService scalarParserService) {
        this.matchNormalizerService = matchNormalizerService;
        this.scalarParserService = scalarParserService;
    }

    public SlaRoutingRuleCandidateContextService() {
        this(new SlaRoutingRuleMatchNormalizerService(), new SlaRoutingRuleScalarParserService());
    }

    public CandidateContext build(Map<String, Object> candidate) {
        return new CandidateContext(
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("channel")),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("business")),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("location")),
                matchNormalizerService.parseCandidateCategories(candidate == null ? null : candidate.get("categories")),
                matchNormalizerService.normalizeMatchValue(candidate == null ? null : candidate.get("client_status")),
                scalarParserService.parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("unread_count")),
                scalarParserService.parseOptionalNonNegativeInt(candidate == null ? null : candidate.get("rating")),
                scalarParserService.parseOptionalLong(candidate == null ? null : candidate.get("minutes_left")),
                matchNormalizerService.normalizeSlaState(candidate == null ? null : candidate.get("sla_state")),
                scalarParserService.trimToNull(String.valueOf(candidate == null ? null : candidate.get("request_number"))),
                ticketId(candidate)
        );
    }

    public String ticketId(Map<String, Object> candidate) {
        String ticketId = scalarParserService.trimToNull(String.valueOf(candidate == null ? null : candidate.get("ticket_id")));
        return ticketId != null ? ticketId : "unknown";
    }

    public record CandidateContext(String channel,
                                   String business,
                                   String location,
                                   Set<String> categories,
                                   String clientStatus,
                                   Integer unreadCount,
                                   Integer rating,
                                   Long minutesLeft,
                                   String slaState,
                                   String requestNumber,
                                   String ticketId) {
    }
}
