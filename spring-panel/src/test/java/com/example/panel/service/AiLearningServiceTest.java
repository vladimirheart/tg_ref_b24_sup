package com.example.panel.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiLearningServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AiPolicyService aiPolicyService;

    private AiLearningService service;

    @BeforeEach
    void setUp() {
        service = new AiLearningService(jdbcTemplate, aiPolicyService);
        when(aiPolicyService.normalizeStatus(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiPolicyService.normalizeTrustLevel(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(aiPolicyService.normalizeSourceType(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(1));
        when(aiPolicyService.normalizeSafetyLevel(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void insertsNewReplyAsDraftPendingReview() {
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(List.of());
        doReturn(1).when(jdbcTemplate).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());

        AiLearningService.UpsertResult result = service.upsertLearningSolution(
                "T-100",
                "Где мой заказ?",
                "Проверим статус и вернемся с ответом.",
                "operator-1",
                0.42d
        );

        assertNotNull(result);
        assertEquals("inserted_draft", result.action());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), org.mockito.ArgumentMatchers.<Object[]>any());
        assertTrue(
                sqlCaptor.getAllValues().stream().anyMatch(sql ->
                        sql.contains("review_required") && sql.contains("pending_solution_text")),
                "Ожидался INSERT/UPDATE с review_required и pending_solution_text"
        );
    }

    @Test
    void keepsPendingReviewWithoutAutoApproveWhenReplyMatchesPending() {
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object[]>any())).thenReturn(List.of(Map.of(
                "solution_text", "Старое решение",
                "pending_solution_text", "Новая формулировка",
                "review_required", 1
        )));
        doReturn(1).when(jdbcTemplate).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());

        AiLearningService.UpsertResult result = service.upsertLearningSolution(
                "T-200",
                "Проблема с оплатой",
                "Новая формулировка",
                "operator-2",
                0.42d
        );

        assertNotNull(result);
        assertEquals("pending_unchanged", result.action());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), org.mockito.ArgumentMatchers.<Object[]>any());
        assertTrue(
                sqlCaptor.getAllValues().stream().noneMatch(sql -> sql.contains("solution_text=?")),
                "Не должно происходить авто-аппрува pending -> solution_text без review"
        );
    }
}
