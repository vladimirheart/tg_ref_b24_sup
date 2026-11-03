package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.entity.*;
import com.example.panel.repository.*;
import com.example.panel.service.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real root ingestion and byte validator; mocked persistence proves rejection before the first domain write. */
class SelectedQuestionnaireMediaIngestionTest {
    private final IntegrationInboundEventInboxService inbox=mock(IntegrationInboundEventInboxService.class);
    private final LegacyTicketIdConcurrencyGuard concurrency=mock(LegacyTicketIdConcurrencyGuard.class);
    private final ChannelRepository channels=mock(ChannelRepository.class);
    private final MessageRepository messages=mock(MessageRepository.class);
    private final TicketRepository tickets=mock(TicketRepository.class);
    private final TicketSpanRepository spans=mock(TicketSpanRepository.class);
    private final TicketActiveRepository active=mock(TicketActiveRepository.class);
    private final ChatHistoryRepository history=mock(ChatHistoryRepository.class);
    private final ChatAttachmentMetadataService metadata=mock(ChatAttachmentMetadataService.class);
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final BusinessTicketWriteScopeResolver resolver=mock(BusinessTicketWriteScopeResolver.class);
    private final SharedGeneralQuestionnaireService shared=mock(SharedGeneralQuestionnaireService.class);
    private final AttachmentObjectStorageService storage=mock(AttachmentObjectStorageService.class);
    private final SelectedQuestionnaireMediaValidator validator=spy(new SelectedQuestionnaireMediaValidator(storage));
    private final Channel channel=new Channel();
    private final ConversationTicketCreationIngestionService service;
    SelectedQuestionnaireMediaIngestionTest() {
        var flags=new MultibusinessFeatureFlags();flags.getFoundation().setEnabled(true);flags.setTicketWriteRequiresBusiness(true);
        flags.setSharedChannelResolution(true);flags.setGeneralChannelResolution(true);
        channel.setId(55L);channel.setPlatform("max");channel.setActive(true);
        when(channels.findById(55L)).thenReturn(Optional.of(channel));
        when(inbox.beginProcessing(anyString(),anyString(),any(),eq(55L),anyString(),anyString(),any(),any())).thenReturn(true);
        when(history.save(any())).thenAnswer(call->{ChatHistory row=call.getArgument(0);row.setId(101L);return row;});
        service=new ConversationTicketCreationIngestionService(inbox,concurrency,channels,messages,tickets,spans,active,history,metadata,jdbc,
            flags,resolver,mock(GeneralChannelQuestionTemplateService.class),mock(BusinessQuestionTemplateRegistryService.class),shared,validator);
    }
    private void selected(ConversationTicketCreatedEvent event,int version) {
        var template=Map.of("id","questionnaire","question_flow",List.of(Map.of("id","free","type","text","text","Question")));
        var selected=new SharedGeneralQuestionnaireService.Selected(version,55,event.scopeKind(),"USER_SELECTION",event.businessId(),
            event.questionTemplateId(),event.templateSha256(),event.catalogSha256(),Map.of("question_templates",List.of(template)),Map.of(),Map.of());
        if(version==4)when(shared.selectExpanded(55,event.scopeKind(),event.businessId(),event.catalogSha256())).thenReturn(Optional.of(selected));
        else when(shared.select(55,event.scopeKind(),event.businessId(),event.catalogSha256())).thenReturn(Optional.of(selected));
    }
    static Stream<Arguments> mediaCases() {
        return Stream.of(false,true).flatMap(general->Stream.of("legacy","external","scoped-max","no-key","empty-key","text-with-key")
            .map(shape->Arguments.of(general,shape)));
    }
    private ConversationTicketCreatedEvent event(boolean general,String platform,String shape) {
        return SelectedQuestionnaireMediaValidatorTest.event(general,platform,List.of(SelectedQuestionnaireMediaValidatorTest.entry(
            shape.equals("text-with-key")||shape.equals("plain-text")?"text":"document",shape.equals("plain-text")?null:SelectedQuestionnaireMediaValidatorTest.unsupportedKey(shape))));
    }
    private void noDomainWrites() {
        verify(messages,never()).save(any());verify(tickets,never()).save(any());
        verifyNoInteractions(spans,active,history,metadata,jdbc,storage);
    }
    @ParameterizedTest @MethodSource("mediaCases")
    void maxVersion4MediaFailsBeforeEveryRootAndChildWrite(boolean general,String shape) {
        var event=event(general,"max",shape);selected(event,4);
        assertThatThrownBy(()->service.ingest(event,"integration.ticket.max")).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Invalid selected questionnaire media proof");
        verify(validator).validate(event,4);noDomainWrites();
        verify(inbox).markFailed(eq(event.eventId()),any(IllegalStateException.class));verify(inbox,never()).markProcessed(anyString());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void maxVersion4PlainTextStillCreatesFrozenRootAndHistory(boolean general) {
        var event=event(general,"max","plain-text");selected(event,4);service.ingest(event,"integration.ticket.max");
        var captor=org.mockito.ArgumentCaptor.forClass(Ticket.class);verify(tickets).save(captor.capture());
        assertThat(captor.getValue().getQuestionnaireVersion()).isEqualTo(4);assertThat(captor.getValue().getBusinessId()).isEqualTo(event.businessId());
        assertThat(captor.getValue().isGeneral()).isEqualTo(general);verify(history).save(any());verify(inbox).markProcessed(event.eventId());verifyNoInteractions(storage);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void maxVersion4OwnedMediaCreatesFrozenRootHistoryAndVerifiedMetadata(boolean general) throws Exception {
        byte[] bytes={1,2,3};String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        String key="questionnaires/max/55/"+(general?"general":"business-7")+"/1001/"+"c".repeat(64)+"/33/1/"+sha+".bin";
        var entry=new ConversationTicketCreatedEvent.ConversationHistoryEntryPayload(1001L,"Caption","document",key,"file.bin","33",SelectedQuestionnaireMediaValidatorTest.NOW);
        var event=SelectedQuestionnaireMediaValidatorTest.event(general,"max",List.of(entry));selected(event,4);
        when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new AttachmentObjectStorageService.StoredBinary(key,"application/octet-stream",3,new java.io.ByteArrayInputStream(bytes)));
        service.ingest(event,"integration.ticket.max");verify(tickets).save(any());verify(history).save(any());
        verify(metadata).upsertForChatHistory(eq(101L),eq(event.ticketId()),eq(55L),eq(key),eq("file.bin"),isNull(),eq(3L),eq("document"),eq(sha));
        verify(inbox).markProcessed(event.eventId());
    }
    @ParameterizedTest @CsvSource({"false,2","true,2","false,3","true,3"})
    void earlierMaxSelectedMediaKeepsLegacyRootCreation(boolean general,int version) {
        var event=event(general,"max","legacy");selected(event,version);service.ingest(event,"integration.ticket.max");
        verify(validator).validate(event,version);verify(tickets).save(any());verify(history).save(any());verify(inbox).markProcessed(event.eventId());verifyNoInteractions(storage);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void matchingFrozenRootReplayDoesNotReopenOrRevalidateHistoricalMedia(boolean general) {
        var event=event(general,"max","legacy");var root=new Ticket();root.setChannel(channel);root.setBusinessId(event.businessId());root.setGeneral(general);
        if(general)root.setGeneralSelectionSource("USER_SELECTION");else root.setBusinessSelectionSource("USER_SELECTION");
        root.setQuestionnaireVersion(4);root.setQuestionTemplateId(event.questionTemplateId());root.setQuestionTemplateSha256(event.templateSha256());root.setQuestionnaireCatalogSha256(event.catalogSha256());
        when(tickets.existsById(any(TicketId.class))).thenReturn(true);when(tickets.findById(any(TicketId.class))).thenReturn(Optional.of(root));
        service.ingest(event,"integration.ticket.max");noDomainWrites();verifyNoInteractions(validator,shared);verify(inbox).markProcessed(event.eventId());
    }
    static Stream<Arguments> platformCases() {
        return Stream.of(false,true).flatMap(general->Stream.of("telegram","vk","unknown",(String)null).map(platform->Arguments.of(general,platform)));
    }
    @ParameterizedTest @MethodSource("platformCases")
    void eventPlatformCannotPretendThatMaxChannelHasAnotherMediaWriter(boolean general,String platform) {
        var event=event(general,platform,"legacy");selected(event,4);
        assertThatThrownBy(()->service.ingest(event,"integration.ticket.max")).isInstanceOf(IllegalStateException.class).hasMessageContaining("platform mismatch");
        noDomainWrites();verifyNoInteractions(validator);verify(inbox).markFailed(eq(event.eventId()),any(IllegalStateException.class));
    }
}
