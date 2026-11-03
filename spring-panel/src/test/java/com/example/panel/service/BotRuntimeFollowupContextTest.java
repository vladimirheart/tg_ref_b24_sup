package com.example.panel.service;

import com.example.panel.entity.*;
import com.example.panel.repository.*;
import java.util.*;
import java.util.stream.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Frozen immutable root metadata, independent of current channel questionnaire bindings. */
class BotRuntimeFollowupContextTest {
    static Object field(Object value,String name)throws Exception{
        try{return value.getClass().getMethod(name).invoke(value);}catch(NoSuchMethodException beforeProductChange){return null;}
    }
    static Stream<Arguments> roots(){return Stream.of(1001L,9007199254740993L,Long.MAX_VALUE).flatMap(user->Stream.<Integer>of(null,1,2,3,4,5).map(version->Arguments.of(user,version)));}
    @ParameterizedTest @MethodSource("roots") void uniqueRootCarriesItsExactChannelAndFrozenVersion(long user,Integer version)throws Exception{
        var repository=mock(TicketRepository.class);var service=new BotRuntimeTicketReadService(mock(MessageRepository.class),repository,mock(TicketActiveRepository.class),mock(FeedbackRepository.class),mock(PendingFeedbackRequestRepository.class));
        var ticket=new Ticket();var id=new TicketId();id.setUserId(user);id.setTicketId(" raw-ticket ");ticket.setId(id);ticket.setStatus("open");
        var channel=new Channel();channel.setId(55L);ticket.setChannel(channel);ticket.setQuestionnaireVersion(version);
        when(repository.findUniqueByLegacyTicketId(" raw-ticket ")).thenReturn(Optional.of(ticket));
        var result=service.findTicket(" raw-ticket ").orElseThrow();assertThat(result.userId()).isEqualTo(user);assertThat(result.ticketId()).isEqualTo(" raw-ticket ");
        assertThat(field(result,"channelId")).isEqualTo(55L);assertThat(field(result,"questionnaireVersion")).isEqualTo(version);assertThat(field(result,"followupContextVersion")).isEqualTo(1);
        var json=Jackson2ObjectMapperBuilder.json().build().valueToTree(result);assertThat(json.path("userId").asLong()).isEqualTo(user);assertThat(json.path("channelId").asLong()).isEqualTo(55L);assertThat(json.path("followupContextVersion").asInt()).isEqualTo(1);
        verify(repository).findUniqueByLegacyTicketId(" raw-ticket ");verifyNoMoreInteractions(repository);
    }
    @ParameterizedTest @ValueSource(longs={-1,0}) void invalidCanonicalOwnerNeverClaimsAnAdmissionContext(long user)throws Exception{
        var repository=mock(TicketRepository.class);var service=new BotRuntimeTicketReadService(mock(MessageRepository.class),repository,mock(TicketActiveRepository.class),mock(FeedbackRepository.class),mock(PendingFeedbackRequestRepository.class));
        var ticket=new Ticket();var id=new TicketId();id.setUserId(user);id.setTicketId("root");ticket.setId(id);var channel=new Channel();channel.setId(55L);ticket.setChannel(channel);ticket.setQuestionnaireVersion(4);
        when(repository.findUniqueByLegacyTicketId("root")).thenReturn(Optional.of(ticket));assertThat(field(service.findTicket("root").orElseThrow(),"followupContextVersion")).isNull();
    }
    @Test void absentChannelNeverClaimsAContext()throws Exception{
        var repository=mock(TicketRepository.class);var service=new BotRuntimeTicketReadService(mock(MessageRepository.class),repository,mock(TicketActiveRepository.class),mock(FeedbackRepository.class),mock(PendingFeedbackRequestRepository.class));
        var ticket=new Ticket();var id=new TicketId();id.setUserId(1001L);id.setTicketId("root");ticket.setId(id);ticket.setQuestionnaireVersion(4);
        when(repository.findUniqueByLegacyTicketId("root")).thenReturn(Optional.of(ticket));assertThat(field(service.findTicket("root").orElseThrow(),"followupContextVersion")).isNull();
    }
    @Test void ambiguousOrAbsentRootDoesNotInventMetadata(){
        var repository=mock(TicketRepository.class);var service=new BotRuntimeTicketReadService(mock(MessageRepository.class),repository,mock(TicketActiveRepository.class),mock(FeedbackRepository.class),mock(PendingFeedbackRequestRepository.class));
        assertThat(service.findTicket("ambiguous")).isEmpty();verify(repository).findUniqueByLegacyTicketId("ambiguous");
    }
}
