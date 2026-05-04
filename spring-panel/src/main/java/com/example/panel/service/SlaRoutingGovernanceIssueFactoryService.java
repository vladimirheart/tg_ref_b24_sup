package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SlaRoutingGovernanceIssueFactoryService {

    public Map<String, Object> buildGovernanceIssue(String classification,
                                                    String status,
                                                    String type,
                                                    String ruleId,
                                                    String summary,
                                                    String detail,
                                                    List<String> ticketIds,
                                                    List<String> relatedRulesOrMeta) {
        return Map.of(
                "classification", classification,
                "status", status,
                "type", type,
                "rule_id", ruleId,
                "summary", summary,
                "detail", detail == null ? "" : detail,
                "tickets", ticketIds == null ? List.of() : ticketIds,
                "related", relatedRulesOrMeta == null ? List.of() : relatedRulesOrMeta
        );
    }
}
