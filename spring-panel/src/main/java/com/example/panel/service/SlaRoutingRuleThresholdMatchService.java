package com.example.panel.service;

import org.springframework.stereotype.Service;

@Service
public class SlaRoutingRuleThresholdMatchService {

    public boolean matches(SlaRoutingRuleTypes.AutoAssignRule rule,
                           Integer candidateUnreadCount,
                           Integer candidateRating,
                           Long candidateMinutesLeft,
                           String candidateSlaState) {
        if (rule == null) {
            return false;
        }
        if (rule.unreadMin() != null && (candidateUnreadCount == null || candidateUnreadCount < rule.unreadMin())) {
            return false;
        }
        if (rule.unreadMax() != null && (candidateUnreadCount == null || candidateUnreadCount > rule.unreadMax())) {
            return false;
        }
        if (rule.ratingMin() != null && (candidateRating == null || candidateRating < rule.ratingMin())) {
            return false;
        }
        if (rule.ratingMax() != null && (candidateRating == null || candidateRating > rule.ratingMax())) {
            return false;
        }
        if (rule.minutesLeftLte() != null && (candidateMinutesLeft == null || candidateMinutesLeft > rule.minutesLeftLte())) {
            return false;
        }
        if (rule.minutesLeftGte() != null && (candidateMinutesLeft == null || candidateMinutesLeft < rule.minutesLeftGte())) {
            return false;
        }
        return rule.slaStates() == null || rule.slaStates().isEmpty() || (candidateSlaState != null && rule.slaStates().contains(candidateSlaState));
    }

    public boolean isEmptyRule(SlaRoutingRuleTypes.AutoAssignRule rule) {
        return rule == null
                || (rule.unreadMin() == null
                && rule.unreadMax() == null
                && rule.ratingMin() == null
                && rule.ratingMax() == null
                && rule.minutesLeftLte() == null
                && rule.minutesLeftGte() == null
                && rule.slaStates().isEmpty());
    }
}
