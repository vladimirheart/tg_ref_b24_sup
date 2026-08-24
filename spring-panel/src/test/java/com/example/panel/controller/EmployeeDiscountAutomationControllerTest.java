package com.example.panel.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.service.EmployeeDiscountAutomationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

class EmployeeDiscountAutomationControllerTest {

    @Test
    void executeForwardsExplicitDeduplicatedTaskSelection() {
        EmployeeDiscountAutomationService service = mock(EmployeeDiscountAutomationService.class);
        EmployeeDiscountAutomationController controller = new EmployeeDiscountAutomationController(service);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");
        when(service.run(false, "alice", List.of("101", "102"))).thenReturn(Map.of("success", true));

        controller.run(Map.of(
            "dry_run", false,
            "selected_task_ids", List.of("101", "102", "101")
        ), authentication);

        verify(service).run(false, "alice", List.of("101", "102"));
    }

    @Test
    void executeWithoutExplicitSelectionIsRejectedBeforeServiceCall() {
        EmployeeDiscountAutomationService service = mock(EmployeeDiscountAutomationService.class);
        EmployeeDiscountAutomationController controller = new EmployeeDiscountAutomationController(service);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("alice");

        assertThatThrownBy(() -> controller.run(Map.of("dry_run", false), authentication))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("явного выбора");
    }
}
