package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.Ticket;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MaintenanceTasksTest {

    @Test
    void resolveAutoCloseDurationUsesSharedSettingInHours() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 1));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                mock(TicketService.class),
                sharedConfigService
        );

        assertThat(tasks.resolveAutoCloseDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void resolveAutoCloseDurationUsesActiveAutoCloseTemplate() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_config", Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-1", "hours", 1),
                                Map.of("id", "auto-2", "hours", 12)
                        ),
                        "active_template_id", "auto-1"
                ),
                "auto_close_hours", 24
        ));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                mock(TicketService.class),
                sharedConfigService
        );

        assertThat(tasks.resolveAutoCloseDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void resolveAutoCloseDurationUsesDeprecatedTemplateAutoCloseHoursAsCompatibilityInput() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_config", Map.of(
                        "templates", List.of(
                                Map.of("id", "auto-1", "auto_close_hours", 1),
                                Map.of("id", "auto-2", "hours", 12)
                        ),
                        "active_template_id", "auto-1"
                )
        ));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                mock(TicketService.class),
                sharedConfigService
        );

        assertThat(tasks.resolveAutoCloseDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void resolveAutoCloseDurationDoesNotFallbackToLegacyHoursWhenAutoCloseConfigAlreadyExists() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_config", Map.of("templates", List.of()),
                "auto_close_hours", 1
        ));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                mock(TicketService.class),
                sharedConfigService
        );

        assertThat(tasks.resolveAutoCloseDuration()).isEqualTo(Duration.ofHours(24));
    }

    @Test
    void resolveAutoCloseDurationDisablesAutoCloseWhenHoursIsZero() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 0));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                mock(TicketService.class),
                sharedConfigService
        );

        assertThat(tasks.resolveAutoCloseDuration()).isNull();
    }

    @Test
    void autoCloseInactiveTicketsPassesLegacyDurationToTicketServiceResolver() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 1));
        when(ticketService.closeInactiveTickets(org.mockito.ArgumentMatchers.<Function<Ticket, TicketService.AutoClosePolicy>>any()))
                .thenReturn(new TicketService.AutoCloseRunResult(1, 1));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                ticketService,
                sharedConfigService
        );

        tasks.autoCloseInactiveTickets();

        ArgumentCaptor<Function<Ticket, TicketService.AutoClosePolicy>> captor = ArgumentCaptor.forClass(Function.class);
        verify(ticketService).closeInactiveTickets(captor.capture());

        Ticket ticket = new Ticket();
        TicketService.AutoClosePolicy policy = captor.getValue().apply(ticket);
        assertThat(policy.enabled()).isTrue();
        assertThat(policy.inactivityLimit()).isEqualTo(Duration.ofHours(1));
        assertThat(policy.source()).isEqualTo("migration:auto_close_hours");
    }

    @Test
    void autoCloseInactiveTicketsDoesNotUseLegacyHoursWhenCanonicalConfigExistsButIsEmpty() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_hours", 1,
                "auto_close_config", Map.of("templates", List.of())
        ));
        when(ticketService.closeInactiveTickets(org.mockito.ArgumentMatchers.<Function<Ticket, TicketService.AutoClosePolicy>>any()))
                .thenReturn(new TicketService.AutoCloseRunResult(1, 0));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                ticketService,
                sharedConfigService
        );

        tasks.autoCloseInactiveTickets();

        ArgumentCaptor<Function<Ticket, TicketService.AutoClosePolicy>> captor = ArgumentCaptor.forClass(Function.class);
        verify(ticketService).closeInactiveTickets(captor.capture());

        TicketService.AutoClosePolicy policy = captor.getValue().apply(new Ticket());
        assertThat(policy.enabled()).isTrue();
        assertThat(policy.inactivityLimit()).isEqualTo(Duration.ofHours(24));
        assertThat(policy.source()).isEqualTo("default:auto_close");
    }

    @Test
    void autoCloseInactiveTicketsUsesChannelAutoActionTemplateOverride() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_hours", 24,
                "auto_close_config", Map.of(
                        "templates", List.of(
                                Map.of("id", "global-template", "hours", 6),
                                Map.of("id", "channel-template", "hours", 1)
                        ),
                        "active_template_id", "global-template"
                )
        ));
        when(ticketService.closeInactiveTickets(org.mockito.ArgumentMatchers.<Function<Ticket, TicketService.AutoClosePolicy>>any()))
                .thenReturn(new TicketService.AutoCloseRunResult(1, 1));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                ticketService,
                sharedConfigService
        );

        tasks.autoCloseInactiveTickets();

        ArgumentCaptor<Function<Ticket, TicketService.AutoClosePolicy>> captor = ArgumentCaptor.forClass(Function.class);
        verify(ticketService).closeInactiveTickets(captor.capture());

        Channel channel = new Channel();
        channel.setId(10L);
        channel.setAutoActionTemplateId("channel-template");
        Ticket ticket = new Ticket();
        ticket.setChannel(channel);

        TicketService.AutoClosePolicy policy = captor.getValue().apply(ticket);
        assertThat(policy.enabled()).isTrue();
        assertThat(policy.inactivityLimit()).isEqualTo(Duration.ofHours(1));
        assertThat(policy.source()).isEqualTo("channel:auto_action_template_id");
        assertThat(policy.templateId()).isEqualTo("channel-template");
    }

    @Test
    void autoCloseInactiveTicketsDisablesPolicyWhenTemplateHoursIsZero() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "auto_close_config", Map.of(
                        "templates", List.of(Map.of("id", "disabled-template", "hours", 0)),
                        "active_template_id", "disabled-template"
                )
        ));
        when(ticketService.closeInactiveTickets(org.mockito.ArgumentMatchers.<Function<Ticket, TicketService.AutoClosePolicy>>any()))
                .thenReturn(new TicketService.AutoCloseRunResult(0, 0));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                ticketService,
                sharedConfigService
        );

        tasks.autoCloseInactiveTickets();

        ArgumentCaptor<Function<Ticket, TicketService.AutoClosePolicy>> captor = ArgumentCaptor.forClass(Function.class);
        verify(ticketService).closeInactiveTickets(captor.capture());

        TicketService.AutoClosePolicy policy = captor.getValue().apply(new Ticket());
        assertThat(policy.enabled()).isFalse();
        assertThat(policy.inactivityLimit()).isNull();
        assertThat(policy.source()).isEqualTo("auto_close_config.active_template");
    }

    @Test
    void autoCloseInactiveTicketsSkipsTicketServiceWhenResolverIsNotCalledNowhere() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 0));
        when(ticketService.closeInactiveTickets(org.mockito.ArgumentMatchers.<Function<Ticket, TicketService.AutoClosePolicy>>any()))
                .thenReturn(new TicketService.AutoCloseRunResult(0, 0));

        MaintenanceTasks tasks = new MaintenanceTasks(
                mock(ClientUnblockRequestRepository.class),
                ticketService,
                sharedConfigService
        );

        tasks.autoCloseInactiveTickets();

        verify(ticketService).closeInactiveTickets(org.mockito.ArgumentMatchers.<Function<Ticket, TicketService.AutoClosePolicy>>any());
    }
}
