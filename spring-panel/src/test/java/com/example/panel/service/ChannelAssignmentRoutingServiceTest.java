package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ChannelAssignmentRoutingServiceTest {

    @Test
    void resolvesSingleOperatorAndImmediateAssignee() {
        SharedConfigService sharedConfigService = Mockito.mock(SharedConfigService.class);
        DialogParticipantService dialogParticipantService = Mockito.mock(DialogParticipantService.class);
        DialogLookupReadService dialogLookupReadService = Mockito.mock(DialogLookupReadService.class);
        when(sharedConfigService.loadOrgStructure()).thenReturn(null);
        when(dialogParticipantService.loadAssignableOperators()).thenReturn(List.of(
                new DialogOperatorOption("alice", "Alice", null, "Support", "operator"),
                new DialogOperatorOption("bob", "Bob", null, "Support", "operator")
        ));
        ChannelAssignmentRoutingService service = new ChannelAssignmentRoutingService(
                new ObjectMapper(),
                sharedConfigService,
                dialogParticipantService,
                dialogLookupReadService
        );

        Channel channel = new Channel();
        channel.setQuestionsCfg("""
                {
                  "assignmentRouting": {
                    "enabled": true,
                    "mode": "single_operator",
                    "assignResponsibleOnCreate": true,
                    "operatorUsername": "alice"
                  }
                }
                """);

        ChannelAssignmentRoutingService.ResolvedAssignmentRouting routing =
                service.resolve(channel, ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL, "T-1");

        assertThat(routing.enabled()).isTrue();
        assertThat(routing.recipients()).containsExactly("alice");
        assertThat(routing.assignee()).isEqualTo("alice");
    }

    @Test
    void resolvesLeastLoadedDepartmentAssignee() {
        SharedConfigService sharedConfigService = Mockito.mock(SharedConfigService.class);
        DialogParticipantService dialogParticipantService = Mockito.mock(DialogParticipantService.class);
        DialogLookupReadService dialogLookupReadService = Mockito.mock(DialogLookupReadService.class);
        when(sharedConfigService.loadOrgStructure()).thenReturn(null);
        when(dialogParticipantService.loadAssignableOperators()).thenReturn(List.of(
                new DialogOperatorOption("busy", "Busy", null, "Support", "operator"),
                new DialogOperatorOption("free", "Free", null, "Support", "operator"),
                new DialogOperatorOption("sales", "Sales", null, "Sales", "operator")
        ));
        when(dialogLookupReadService.loadDialogs("busy")).thenReturn(List.of(
                dialog("T-1", "open", "busy"),
                dialog("T-2", "resolved", "busy"),
                dialog("T-3", "open", "busy")
        ));
        when(dialogLookupReadService.loadDialogs("free")).thenReturn(List.of(
                dialog("T-4", "resolved", "free")
        ));
        ChannelAssignmentRoutingService service = new ChannelAssignmentRoutingService(
                new ObjectMapper(),
                sharedConfigService,
                dialogParticipantService,
                dialogLookupReadService
        );

        Channel channel = new Channel();
        channel.setQuestionsCfg("""
                {
                  "assignmentRouting": {
                    "enabled": true,
                    "mode": "department_queue",
                    "assignResponsibleOnCreate": true,
                    "department": "Support",
                    "strategy": "least_loaded"
                  }
                }
                """);

        ChannelAssignmentRoutingService.ResolvedAssignmentRouting routing =
                service.resolve(channel, ChannelAssignmentRoutingService.RoutingEvent.NEW_PUBLIC_APPEAL, "T-5");

        assertThat(routing.recipients()).containsExactly("busy", "free");
        assertThat(routing.assignee()).isEqualTo("free");
    }

    @Test
    void catalogPayloadMergesDepartmentsFromOperatorsAndOrgStructure() {
        SharedConfigService sharedConfigService = Mockito.mock(SharedConfigService.class);
        DialogParticipantService dialogParticipantService = Mockito.mock(DialogParticipantService.class);
        DialogLookupReadService dialogLookupReadService = Mockito.mock(DialogLookupReadService.class);
        when(sharedConfigService.loadOrgStructure()).thenReturn(new ObjectMapper().valueToTree(Map.of(
                "nodes", List.of(
                        Map.of("name", "Support"),
                        Map.of("name", "Operations")
                )
        )));
        when(dialogParticipantService.loadAssignableOperators()).thenReturn(List.of(
                new DialogOperatorOption("alice", "Alice", null, "Support", "operator"),
                new DialogOperatorOption("bob", "Bob", null, "Sales", "operator")
        ));
        ChannelAssignmentRoutingService service = new ChannelAssignmentRoutingService(
                new ObjectMapper(),
                sharedConfigService,
                dialogParticipantService,
                dialogLookupReadService
        );

        Map<String, Object> payload = service.loadCatalogPayload();

        assertThat(payload.get("departments")).isEqualTo(List.of("Operations", "Sales", "Support"));
        assertThat((List<?>) payload.get("operators")).hasSize(2);
    }

    private DialogListItem dialog(String ticketId, String status, String responsible) {
        return new DialogListItem(
                ticketId,
                100L,
                1L,
                "client",
                "Client",
                "biz",
                10L,
                "Telegram",
                "Moscow",
                "HQ",
                "Issue",
                "2026-07-29T10:00:00Z",
                status,
                null,
                null,
                responsible,
                null,
                null,
                null,
                "user",
                "2026-07-29T10:00:00Z",
                0,
                null,
                null
        );
    }
}
