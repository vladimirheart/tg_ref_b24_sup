package com.example.panel.service;

import com.example.panel.entity.*;
import com.example.panel.repository.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PanelTaskServiceTest {
    private final TaskRepository tasks=mock(TaskRepository.class);
    private final TaskLinkRepository links=mock(TaskLinkRepository.class);
    private final TaskDomainFoundationService domain=mock(TaskDomainFoundationService.class);
    private final SharedWorkAccessService access=mock(SharedWorkAccessService.class);
    private final UiEventTicketAccessService tickets=mock(UiEventTicketAccessService.class);
    private final String raw=" Owner/Ю ";
    private final UiEventTicketScope scope=new UiEventTicketScope(902L," T/100? Ю ","a".repeat(32),"BUSINESS",7L);
    private PanelTaskService service(){
        var service=new PanelTaskService(tasks,mock(TaskSequenceRepository.class),mock(TaskPersonRepository.class),
            mock(TaskHistoryRepository.class),links,mock(NotificationRepository.class),mock(TicketRepository.class),domain);
        ReflectionTestUtils.setField(service,"sharedAccess",access);ReflectionTestUtils.setField(service,"ticketAccess",tickets);
        return service;
    }
    private PanelTaskService.TaskPayload payload(){
        return new PanelTaskService.TaskPayload("Follow up",null,"forged",raw,null,null,null,"dialog_auto_close",
            List.of(),List.of(),List.of(scope.ticketId()),List.of());
    }
    @Test void legacyUnownedCreationIsClosed(){
        assertThatThrownBy(()->service().createTask(payload())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(tasks,links);
    }
    @Test void trustedFollowUpKeepsExactOwnerAndCanonicalTuple(){
        when(access.eligibleReader(raw)).thenReturn(new SharedWorkAccessService.Person("9007199254740993",raw));
        when(access.nextTaskSequence()).thenReturn(42L);
        when(tickets.canReadTicketForRecipient(any(),eq(scope))).thenReturn(true);
        when(tasks.saveAndFlush(any())).thenAnswer(inv->{Task t=inv.getArgument(0);t.setId(77L);return t;});
        Task task=service().createFollowUpTask(payload(),scope);
        assertThat(task.getSharedOwnerUserId()).isEqualTo(9007199254740993L);
        assertThat(task.getAssignee()).isEqualTo(raw);assertThat(task.getCreator()).isEqualTo("auto_close");
        var link=ArgumentCaptor.forClass(TaskLink.class);verify(links).save(link.capture());
        assertThat(link.getValue().getId().getTicketId()).isEqualTo(scope.ticketId());
        assertThat(link.getValue().getId().getUserId()).isEqualTo(902L);
        verify(access).registerSystemCreated(77L,9007199254740993L,raw);
    }
    @Test void missingCurrentModuleOrTicketReadNeverCreatesOrFallsBack(){
        assertThatThrownBy(()->service().createFollowUpTask(payload(),scope)).isInstanceOf(IllegalArgumentException.class);
        when(access.eligibleReader(raw)).thenReturn(new SharedWorkAccessService.Person("11",raw));
        assertThatThrownBy(()->service().createFollowUpTask(payload(),scope)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tasks,links);
    }
}
