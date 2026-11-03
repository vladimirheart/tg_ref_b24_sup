package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.panel.storage.AttachmentObjectStorageService;
import com.example.panel.storage.AttachmentObjectStorageService.StoredBinary;
import com.example.panel.service.integration.ConversationTicketCreatedEvent.ConversationHistoryEntryPayload;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Real byte verifier; synthetic storage only, no working database or provider. */
class SelectedQuestionnaireMediaValidatorTest {
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-11-03T05:00:00Z");
    static final String CATALOG="c".repeat(64);
    static ConversationTicketCreatedEvent event(boolean general,String platform,List<ConversationHistoryEntryPayload> history) {
        return new ConversationTicketCreatedEvent("r273-event","ticket.created.initial_contact",platform,55L,"R273-TICKET",1001L,
            "1001","client","Client",null,null,null,null,"Answer",NOW,
            List.of(new ConversationTicketCreatedEvent.TicketAttributePayload("free","free","Question","text",null,null,"Answer",false)),
            history,general?null:7L,general?"GENERAL":"BUSINESS","USER_SELECTION","questionnaire","a".repeat(64),CATALOG);
    }
    static ConversationHistoryEntryPayload entry(String type,String key) {
        return new ConversationHistoryEntryPayload(1001L,"Private caption",type,key,"proof.bin","33",NOW);
    }
    static String unsupportedKey(String shape) {
        return switch(shape) {
            case "legacy" -> "channel/2026/11/proof.bin";
            case "external" -> "https://example.invalid/private.bin";
            case "scoped-max" -> "questionnaires/max/55/general/private.bin";
            case "scoped-telegram" -> "questionnaires/telegram/55/general/private.bin";
            case "empty-key" -> "";
            case "text-with-key" -> "channel/private-caption.bin";
            case "no-key" -> null;
            default -> throw new IllegalArgumentException(shape);
        };
    }
    static Stream<Arguments> unsupported() {
        return Stream.of(false,true).flatMap(general->Stream.of("max","unknown",(String)null).flatMap(platform->
            Stream.of("legacy","external","scoped-max","scoped-telegram","empty-key","text-with-key","no-key")
                .map(shape->Arguments.of(general,platform,shape))));
    }
    @ParameterizedTest @MethodSource("unsupported")
    void version4UnsupportedMediaCannotUseLegacyCreation(boolean general,String platform,String shape) {
        var storage=mock(AttachmentObjectStorageService.class);
        var validator=new SelectedQuestionnaireMediaValidator(storage);
        var e=event(general,platform,List.of(entry(shape.equals("text-with-key")?"text":"document",unsupportedKey(shape))));
        assertThatThrownBy(()->validator.validate(e,4)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }
    static Stream<Arguments> textCases() {
        return Stream.of(false,true).flatMap(general->Stream.of("max","telegram","vk",(String)null).map(platform->Arguments.of(general,platform)));
    }
    @ParameterizedTest @MethodSource("textCases")
    void version4PlainTextDoesNotNeedMediaStorage(boolean general,String platform) {
        var storage=mock(AttachmentObjectStorageService.class);
        assertThat(new SelectedQuestionnaireMediaValidator(storage).validate(event(general,platform,List.of(entry("text",null))),4)).isEmpty();
        verifyNoInteractions(storage);
    }
    static Stream<Arguments> legacyCases() {
        return Stream.of(false,true).flatMap(general->Stream.of(0,1,2,3).flatMap(version->Stream.of("legacy","external","no-key")
            .map(shape->Arguments.of(general,version,shape))));
    }
    @ParameterizedTest @MethodSource("legacyCases")
    void earlierMaxVersionsKeepTheirLegacyProtocol(boolean general,int version,String shape) {
        var storage=mock(AttachmentObjectStorageService.class);
        assertThat(new SelectedQuestionnaireMediaValidator(storage).validate(event(general,"max",List.of(entry("document",unsupportedKey(shape)))),version)).isEmpty();
        verifyNoInteractions(storage);
    }
    static Stream<Arguments> owners() {
        return Stream.of(false,true).flatMap(general->Stream.of("telegram","vk").map(platform->Arguments.of(general,platform)));
    }
    private static String key(boolean general,String platform,String sha) {
        return "questionnaires/"+platform+"/55/"+(general?"general":"business-7")+"/1001/"+CATALOG+"/33/"+(platform.equals("vk")?"1/":"")+sha+".bin";
    }
    private static String sha(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    @ParameterizedTest @MethodSource("owners")
    void supportedMediaStillVerifiesOwnedBytesAndClosesStream(boolean general,String platform) throws Exception {
        byte[] bytes="synthetic owned proof".getBytes(StandardCharsets.UTF_8);String sha=sha(bytes),key=key(general,platform,sha);
        var storage=mock(AttachmentObjectStorageService.class);var stream=spy(new ByteArrayInputStream(bytes));
        when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new StoredBinary(key,"application/octet-stream",bytes.length,stream));
        assertThat(new SelectedQuestionnaireMediaValidator(storage).validate(event(general,platform,List.of(entry("document",key))),4))
            .containsExactlyEntriesOf(Map.of(key,new SelectedQuestionnaireMediaValidator.Verified(bytes.length,sha)));
        verify(stream).close();verify(storage).openDialogAttachmentByStorageKey(key);verifyNoMoreInteractions(storage);
    }
    static Stream<Arguments> keylessCases() {
        return owners().flatMap(args->Stream.of("document","photo",(String)null)
            .map(type->Arguments.of(args.get()[0],args.get()[1],type)));
    }
    @ParameterizedTest @MethodSource("keylessCases")
    void supportedVersion4MediaWithoutStoredBytesIsNotText(boolean general,String platform,String type) {
        var storage=mock(AttachmentObjectStorageService.class);
        assertThatThrownBy(()->new SelectedQuestionnaireMediaValidator(storage).validate(event(general,platform,List.of(entry(type,null))),4))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(storage);
    }
    static Stream<Arguments> changedBytes() {
        return owners().flatMap(args->Stream.of("hash","empty","oversize","size","missing")
            .map(mutation->Arguments.of(args.get()[0],args.get()[1],mutation)));
    }
    @ParameterizedTest @MethodSource("changedBytes")
    void supportedStorageFailureDoesNotProduceVerifiedMetadata(boolean general,String platform,String mutation) throws Exception {
        byte[] original="expected proof".getBytes(StandardCharsets.UTF_8),body=mutation.equals("hash")?new byte[]{1,2}:original;
        String key=key(general,platform,sha(original));var storage=mock(AttachmentObjectStorageService.class);
        long size=switch(mutation){case "empty"->0;case "oversize"->10*1024*1024+1;case "size"->original.length+1;default->body.length;};
        if(mutation.equals("missing"))when(storage.openDialogAttachmentByStorageKey(key)).thenThrow(new java.io.IOException("fixture missing"));
        else when(storage.openDialogAttachmentByStorageKey(key)).thenReturn(new StoredBinary(key,"application/octet-stream",size,new ByteArrayInputStream(body)));
        assertThatThrownBy(()->new SelectedQuestionnaireMediaValidator(storage).validate(event(general,platform,List.of(entry("document",key))),4))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("unavailable or changed");
    }
}
