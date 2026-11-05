package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.service.ChatAttachmentMetadataService;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Real ingestion, mocked persistence and byte verifier: the canonical platform gate must precede all root/media work. */
class InboundClientPlatformIdentityTest {
    static final long USER=9_007_199_254_740_993L,CHANNEL=USER+2;
    static final String RAW=" padded/Ю%?' ";
    static final List<String> PLATFORMS=List.of("telegram","vk","max");
    static final OffsetDateTime TIME=OffsetDateTime.parse("2026-11-05T10:00:00Z");
    static class Fixture {
        final IntegrationInboundEventInboxService inbox=mock(IntegrationInboundEventInboxService.class);
        final LegacyTicketIdConcurrencyGuard lock=mock(LegacyTicketIdConcurrencyGuard.class);
        final ChannelRepository channels=mock(ChannelRepository.class);
        final TicketRepository tickets=mock(TicketRepository.class);
        final ChatHistoryRepository history=mock(ChatHistoryRepository.class);
        final MessageRepository messages=mock(MessageRepository.class);
        final TicketActiveRepository active=mock(TicketActiveRepository.class);
        final ChatAttachmentMetadataService metadata=mock(ChatAttachmentMetadataService.class);
        final SelectedTicketFollowupMediaService verifier=mock(SelectedTicketFollowupMediaService.class);
        final Channel channel=new Channel();final Ticket root=new Ticket();final Message profile=new Message();
        final InboundClientMessageIngestionService service;
        Fixture(String platform,Integer version,boolean general) {
            channel.setId(CHANNEL);channel.setPlatform(platform);
            var id=new TicketId();id.setUserId(USER);id.setTicketId(RAW);root.setId(id);root.setChannel(channel);root.setQuestionnaireVersion(version);
            root.setBusinessId(general?null:7L);root.setGeneral(general);root.setQuestionnaireCatalogSha256(Integer.valueOf(4).equals(version)?"c".repeat(64):null);
            profile.setTicketId(RAW);profile.setUsername("old-user");profile.setClientName("Old Name");
            when(inbox.beginProcessing(anyString(),anyString(),any(),any(),anyString(),anyString(),any(),any())).thenReturn(true);
            when(channels.findById(CHANNEL)).thenReturn(Optional.of(channel));when(tickets.findById(any(TicketId.class))).thenReturn(Optional.of(root));
            when(history.save(any(ChatHistory.class))).thenAnswer(c->{ChatHistory row=c.getArgument(0);row.setId(101L);return row;});
            when(messages.findFirstByTicketId(RAW)).thenReturn(Optional.of(profile));
            var verified=new SelectedQuestionnaireMediaValidator.Verified(3,"a".repeat(64));when(verifier.verify(any())).thenReturn(verified);
            when(verifier.verifyVkBatch(any())).thenAnswer(c->Map.of(c.<InboundClientMessageEvent>getArgument(0).attachmentPath(),verified));
            when(verifier.verifyMaxBatch(any())).thenAnswer(c->Map.of(c.<InboundClientMessageEvent>getArgument(0).attachmentPath(),verified));
            service=new InboundClientMessageIngestionService(inbox,lock,channels,tickets,history,messages,active,metadata);service.setSelectedMedia(verifier);
        }
        void deniedBeforeRoot(InboundClientMessageEvent event) {
            assertThatThrownBy(()->service.ingest(event,"fixture.inbound")).isInstanceOf(IllegalStateException.class).hasMessageContaining("channel platform mismatch");
            verifyNoInteractions(lock,tickets,history,messages,active,metadata,verifier);
            verify(inbox).markFailed(eq(event.eventId()),any(IllegalStateException.class));verify(inbox,never()).markProcessed(anyString());verify(inbox,never()).markProcessedWithWrites(anyString());
            assertThat(profile.getUsername()).isEqualTo("old-user");assertThat(profile.getClientName()).isEqualTo("Old Name");
        }
    }
    static InboundClientMessageEvent event(String platform,boolean media) {
        boolean batch=media&&!"telegram".equals(platform);String key=media?"questionnaires/"+platform+"/proof.bin":null;
        var items=batch?List.of(new InboundClientMessageEvent.MediaItem("document",key,"proof.bin")):null;
        String kind=batch?InboundClientMessageEvent.MEDIA_BATCH_KIND:"client_message.active_ticket";
        String id=batch?InboundClientMessageEvent.mediaBatchId(CHANNEL,USER,RAW,Long.toString(Long.MAX_VALUE)):"r288-text";
        return new InboundClientMessageEvent(id,kind,platform,CHANNEL,RAW,USER,"new-identity","new-user","New Name","Private caption",
            batch?"selected_media_batch.v1":media?"document":"text",key,media?"proof.bin":null,Long.toString(Long.MAX_VALUE),null,null,TIME,items);
    }
    static Stream<Arguments> matching(){return PLATFORMS.stream().flatMap(p->Arrays.asList(null,1,2,3,4).stream().flatMap(v->Stream.of(false,true).map(g->Arguments.of(p,v,g))));}
    static Stream<Arguments> crossing(){return PLATFORMS.stream().flatMap(p->PLATFORMS.stream().filter(c->!p.equals(c)).flatMap(c->Arrays.asList(null,4).stream().flatMap(v->Stream.of(false,true).map(g->Arguments.of(p,c,v,g)))));}
    static Stream<Arguments> malformed(){return Arrays.asList(null,""," ","MAX","telegram ","unknown").stream().flatMap(value->Stream.of("event","channel","both").map(side->Arguments.of(value,side)));}
    static Stream<Arguments> media(){return PLATFORMS.stream().flatMap(p->Stream.of(false,true).map(g->Arguments.of(p,g)));}
    static Stream<Arguments> crossingMedia(){return PLATFORMS.stream().flatMap(p->PLATFORMS.stream().filter(c->!p.equals(c)).flatMap(c->Stream.of(false,true).map(g->Arguments.of(p,c,g))));}
    @ParameterizedTest @MethodSource("matching")
    void matchingPlatformPreservesTextOwnerProfileActivityAndLegacyVersions(String platform,Integer version,boolean general) {
        var f=new Fixture(platform,version,general);var event=event(platform,false);f.service.ingest(event,"fixture.inbound");
        var saved=org.mockito.ArgumentCaptor.forClass(ChatHistory.class);verify(f.history).save(saved.capture());
        assertThat(saved.getValue().getTicketId()).isEqualTo(RAW);assertThat(saved.getValue().getTicketOwnerUserId()).isEqualTo(USER);assertThat(saved.getValue().getChannel()).isSameAs(f.channel);
        assertThat(saved.getValue().getMessage()).isEqualTo("Private caption");assertThat(saved.getValue().getTgMessageId()).isEqualTo(Long.MAX_VALUE);assertThat(saved.getValue().getTimestamp()).isEqualTo(TIME);
        assertThat(f.profile.getUsername()).isEqualTo("new-user");assertThat(f.profile.getClientName()).isEqualTo("New Name");verify(f.messages).save(f.profile);verify(f.active).save(any());verify(f.inbox).markProcessed(event.eventId());verifyNoInteractions(f.verifier);
        var order=inOrder(f.channels,f.lock,f.tickets,f.history);order.verify(f.channels).findById(CHANNEL);order.verify(f.lock).acquire(RAW);order.verify(f.tickets).findById(any(TicketId.class));order.verify(f.history).save(any());
    }
    @ParameterizedTest @MethodSource("crossing")
    void foreignPlatformTextCannotBecomeAnotherChannelsHistoryOrProfile(String platform,String canonical,Integer version,boolean general){new Fixture(canonical,version,general).deniedBeforeRoot(event(platform,false));}
    @ParameterizedTest @MethodSource("malformed")
    void absentUnknownOrNoncanonicalPlatformNeverInheritsADefault(String value,String side){
        String eventPlatform=side.equals("channel")?"max":value,channelPlatform=side.equals("event")?"max":value;
        new Fixture(channelPlatform,4,false).deniedBeforeRoot(event(eventPlatform,false));
    }
    @ParameterizedTest @MethodSource("media")
    void matchingMediaRetainsItsPlatformSpecificVerifierAndInboxCommit(String platform,boolean general){
        var f=new Fixture(platform,4,general);var event=event(platform,true);f.service.ingest(event,"fixture.inbound");
        verify(f.history).save(any());verify(f.metadata).upsertForChatHistory(101L,RAW,CHANNEL,event.attachmentPath(),event.attachmentName(),null,3L,"document","a".repeat(64));
        switch(platform){case "telegram"->{verify(f.verifier).verify(event);verify(f.verifier,never()).verifyVkBatch(any());verify(f.verifier,never()).verifyMaxBatch(any());verify(f.inbox).markProcessed(event.eventId());}case "vk"->{verify(f.verifier).verifyVkBatch(event);verify(f.verifier,never()).verify(any());verify(f.verifier,never()).verifyMaxBatch(any());verify(f.inbox).markProcessedWithWrites(event.eventId());}case "max"->{verify(f.verifier).verifyMaxBatch(event);verify(f.verifier,never()).verify(any());verify(f.verifier,never()).verifyVkBatch(any());verify(f.inbox).markProcessedWithWrites(event.eventId());}}
    }
    @ParameterizedTest @MethodSource("crossingMedia")
    void foreignMediaIsRejectedBeforeTicketLocksOrAnyByteVerifier(String platform,String canonical,boolean general){new Fixture(canonical,4,general).deniedBeforeRoot(event(platform,true));}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void alreadyProcessedEnvelopeDoesNotReopenPlatformOrBusinessState(boolean media){
        var f=new Fixture("vk",4,false);var event=event("max",media);when(f.inbox.beginProcessing(anyString(),anyString(),any(),any(),anyString(),anyString(),any(),any())).thenReturn(false);
        f.service.ingest(event,"fixture.inbound");verifyNoInteractions(f.channels,f.lock,f.tickets,f.history,f.messages,f.active,f.metadata,f.verifier);verify(f.inbox,never()).markFailed(any(),any());verify(f.inbox,never()).markProcessed(any());verify(f.inbox,never()).markProcessedWithWrites(any());
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void explicitChannelRepairAllowsTheSameUnchangedFailedEnvelope(String platform){
        var f=new Fixture(platform.equals("max")?"vk":"max",4,false);var event=event(platform,false);f.deniedBeforeRoot(event);f.channel.setPlatform(platform);
        f.service.ingest(event,"fixture.inbound");verify(f.history,times(1)).save(any());verify(f.inbox,times(1)).markFailed(eq(event.eventId()),any());verify(f.inbox,times(1)).markProcessed(event.eventId());assertThat(f.root.getBusinessId()).isEqualTo(7L);
    }
}
