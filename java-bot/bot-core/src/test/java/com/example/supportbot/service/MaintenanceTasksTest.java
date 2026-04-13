package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaintenanceTasksTest {

    @Test
    void resolveAutoCloseDurationUsesSharedSettingInHours() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 3));

        MaintenanceTasks tasks = new MaintenanceTasks(mock(com.example.supportbot.repository.ClientUnblockRequestRepository.class),
            ticketService, sharedConfigService);

        assertThat(tasks.resolveAutoCloseDuration()).isEqualTo(Duration.ofHours(3));
    }

    @Test
    void resolveAutoCloseDurationDisablesAutoCloseWhenHoursIsZero() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 0));

        MaintenanceTasks tasks = new MaintenanceTasks(mock(com.example.supportbot.repository.ClientUnblockRequestRepository.class),
            mock(TicketService.class), sharedConfigService);

        assertThat(tasks.resolveAutoCloseDuration()).isNull();
    }

    @Test
    void autoCloseInactiveTicketsPassesConfiguredDurationToTicketService() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 2));

        MaintenanceTasks tasks = new MaintenanceTasks(mock(com.example.supportbot.repository.ClientUnblockRequestRepository.class),
            ticketService, sharedConfigService);

        tasks.autoCloseInactiveTickets();

        verify(ticketService).closeInactiveTickets(Duration.ofHours(2));
    }

    @Test
    void autoCloseInactiveTicketsSkipsTicketServiceWhenDisabled() {
        TicketService ticketService = mock(TicketService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of("auto_close_hours", 0));

        MaintenanceTasks tasks = new MaintenanceTasks(mock(com.example.supportbot.repository.ClientUnblockRequestRepository.class),
            ticketService, sharedConfigService);

        tasks.autoCloseInactiveTickets();

        verifyNoInteractions(ticketService);
    }
}
