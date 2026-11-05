package com.example.panel.service.integration;

import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.service.ChatAttachmentMetadataService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaxFollowupMediaIngestionTest {
    MaxFollowupMediaVerifierTest.Fixture f;
    final IntegrationInboundEventInboxService inbox=mock(IntegrationInboundEventInboxService.class);
    final LegacyTicketIdConcurrencyGuard concurrency=mock(LegacyTicketIdConcurrencyGuard.class);
    final ChannelRepository channels=mock(ChannelRepository.class);final TicketRepository tickets=mock(TicketRepository.class);
    final ChatHistoryRepository history=mock(ChatHistoryRepository.class);final MessageRepository messages=mock(MessageRepository.class);
    final TicketActiveRepository active=mock(TicketActiveRepository.class);final ChatAttachmentMetadataService metadata=mock(ChatAttachmentMetadataService.class);
    Channel channel;Ticket root;InboundClientMessageIngestionService ingestion;
    @BeforeEach void setup()throws Exception{
        f=new MaxFollowupMediaVerifierTest.Fixture();channel=new Channel();channel.setId(55L);channel.setPlatform("max");
        root=new Ticket();var id=new TicketId();id.setUserId(1001L);id.setTicketId(f.ticketId);root.setId(id);root.setChannel(channel);root.setQuestionnaireVersion(4);root.setQuestionnaireCatalogSha256(f.catalog);
        when(channels.findById(55L)).thenReturn(Optional.of(channel));when(tickets.findById(any(TicketId.class))).thenReturn(Optional.of(root));
        when(inbox.beginProcessing(anyString(),anyString(),any(),any(),anyString(),anyString(),any(),any())).thenReturn(true);
        when(history.save(any())).thenAnswer(call->{ChatHistory saved=call.getArgument(0);saved.setId(100L+(long)mockingDetails(history).getInvocations().stream().filter(i->i.getMethod().getName().equals("save")).count());return saved;});
        ingestion=new InboundClientMessageIngestionService(inbox,concurrency,channels,tickets,history,messages,active,metadata);ingestion.setSelectedMedia(f.service);
    }
    @ParameterizedTest @ValueSource(booleans={false,true}) void completeMaxBatchProducesOwnedHistoryAndVerifiedMetadata(boolean general)throws Exception{
        f.root(general,0);var event=f.event("max",Long.toString(Long.MAX_VALUE),2);
        assertThatCode(()->ingestion.ingest(event,"integration.ticket.max")).doesNotThrowAnyException();
        var saved=org.mockito.ArgumentCaptor.forClass(ChatHistory.class);verify(history,times(2)).save(saved.capture());
        for(int i=0;i<2;i++){var row=saved.getAllValues().get(i);var item=event.mediaBatch().get(i);assertThat(row.getTicketOwnerUserId()).isEqualTo(1001L);assertThat(row.getTicketId()).isEqualTo(f.ticketId);assertThat(row.getAttachment()).isEqualTo(item.attachmentPath());assertThat(row.getTgMessageId()).isEqualTo(Long.MAX_VALUE);
            verify(metadata).upsertForChatHistory(eq(row.getId()),eq(f.ticketId),eq(55L),eq(item.attachmentPath()),eq(item.attachmentName()),isNull(),eq(3L),eq(item.messageType()),eq(f.sha));}
        verify(inbox).markProcessedWithWrites(event.eventId());verify(inbox,never()).markProcessed(anyString());verify(active).save(any());
        var order=inOrder(concurrency,tickets,f.storage,history,metadata,inbox);order.verify(concurrency).acquire(f.ticketId);order.verify(tickets).findById(any(TicketId.class));
        for(var item:event.mediaBatch())order.verify(f.storage).openDialogAttachmentByStorageKey(item.attachmentPath());order.verify(history).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"missing","hash","size","quota","closed-or-missing-root","wrong-channel-platform","legacy-kind"})
    void failedBatchCannotLeavePartialHistoryMetadataOrActivity(String mode)throws Exception{
        var event=f.event("max","33",2);f.badKey=event.mediaBatch().get(1).attachmentPath();
        switch(mode){case "missing","hash","size"->f.badBytes=mode;case "quota"->f.root(false,9);case "closed-or-missing-root"->f.rows=0;case "wrong-channel-platform"->channel.setPlatform("vk");case "legacy-kind"->event=f.change(event,"eventKind","client_message.active_ticket");}
        var denied=event;assertThatThrownBy(()->ingestion.ingest(denied,"integration.ticket.max")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(history,metadata,active,messages);verify(inbox).markFailed(eq(event.eventId()),any());verify(inbox,never()).markProcessedWithWrites(anyString());
    }
    @Test void alreadyCompletedEventDoesNotOpenOrRewriteAnyByte()throws Exception{
        var event=f.event("max","33",2);when(inbox.beginProcessing(anyString(),anyString(),any(),any(),anyString(),anyString(),any(),any())).thenReturn(false);
        ingestion.ingest(event,"integration.ticket.max");verifyNoInteractions(channels,tickets,concurrency,f.jdbc,f.storage,history,metadata,active,messages);verify(inbox,never()).markProcessedWithWrites(anyString());
    }
}
