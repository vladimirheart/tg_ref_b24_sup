package com.example.panel.service;

import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeWorkload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RuntimeWorkload(
    id = "ai-offline-evaluation-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)
public class AiOfflineEvaluationScheduler {

    private final AiOfflineEvaluationService evaluationService;

    public AiOfflineEvaluationScheduler(AiOfflineEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @Scheduled(cron = "${panel.ai.offline-eval.cron:0 15 3 * * *}")
    public void runScheduledEvaluation() {
        evaluationService.runScheduledEvaluation();
    }
}
