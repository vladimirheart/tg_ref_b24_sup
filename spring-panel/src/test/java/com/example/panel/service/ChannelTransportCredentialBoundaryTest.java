package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChannelTransportCredentialBoundaryTest {
    private static final long ID = 9_007_199_254_740_993L;
    private static final long CREDENTIAL = 7L;
    private static final List<String> OPERATIONS = List.of("test", "support", "broadcast", "custom", "resolve");
    static Stream<Arguments> invalidSenders() {
        return OPERATIONS.stream().flatMap(op -> Stream.of("missing", "no-id", "zero-id", "inactive", "unknown-active",
            "vk", "max", "unknown-platform", "missing-platform", "empty-inline", "null-inline", "zero-binding",
            "negative-binding", "missing-binding", "duplicate-binding", "inactive-binding", "unknown-binding-active",
            "foreign-platform-binding", "empty-binding-token", "null-binding-token", "null-catalog", "unavailable-catalog")
            .map(change -> Arguments.of(op, change)));
    }
    static Stream<Arguments> validSenders() {
        return OPERATIONS.stream().flatMap(op -> Stream.of(false, true).map(bound -> Arguments.of(op, bound)));
    }
    static Stream<String> sendOperations() { return Stream.of("test", "support", "broadcast", "custom"); }

    @ParameterizedTest @MethodSource("invalidSenders")
    void invalidCredentialNeverTouchesProviderOrNetwork(String operation, String change) throws Exception {
        var f = new Fixture();
        switch (change) {
            case "missing" -> when(f.channels.findById(ID)).thenReturn(Optional.empty());
            case "no-id" -> f.channel.setId(null);
            case "zero-id" -> f.channel.setId(0L);
            case "inactive" -> f.channel.setActive(false);
            case "unknown-active" -> f.channel.setActive(null);
            case "vk", "max" -> f.channel.setPlatform(change);
            case "unknown-platform" -> f.channel.setPlatform("mail");
            case "missing-platform" -> f.channel.setPlatform(null);
            case "empty-inline" -> f.channel.setToken("  ");
            case "null-inline" -> f.channel.setToken(null);
            case "zero-binding" -> f.channel.setCredentialId(0L);
            case "negative-binding" -> f.channel.setCredentialId(-1L);
            case "missing-binding" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of()); }
            case "duplicate-binding" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(true,"telegram","selected"),f.credential(true,"telegram","other"))); }
            case "inactive-binding" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(false,"telegram","selected"))); }
            case "unknown-binding-active" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(null,"telegram","selected"))); }
            case "foreign-platform-binding" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(true,"max","selected"))); }
            case "empty-binding-token" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(true,"telegram","  "))); }
            case "null-binding-token" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(true,"telegram",null))); }
            case "null-catalog" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenReturn(null); }
            case "unavailable-catalog" -> { f.channel.setCredentialId(CREDENTIAL); when(f.config.loadBotCredentials()).thenThrow(new IllegalStateException("private credential unavailable")); }
            default -> throw new AssertionError(change);
        }
        assertThat(f.execute(operation)).isFalse();
        verifyNoInteractions(f.network, f.http);
    }

    @ParameterizedTest @MethodSource("validSenders")
    void selectsOnlyEffectiveCredentialAndExactConfiguredTarget(String operation, boolean bound) throws Exception {
        var f = new Fixture();
        if (bound) { f.channel.setCredentialId(CREDENTIAL); f.channel.setToken(""); }
        assertThat(f.execute(operation)).isTrue();
        if (operation.equals("resolve")) { verifyNoInteractions(f.network,f.http); return; }
        var request = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(f.http).send(request.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().getPath()).isEqualTo("/bot"+(bound?"selected":"old-inline")+"/sendMessage");
        assertThat(request.getValue().method()).isEqualTo("POST");
        verify(f.channels, never()).save(any());
    }

    @ParameterizedTest @MethodSource("sendOperations")
    void doesNotCacheAllowAcrossCredentialRevocation(String operation) throws Exception {
        var f = new Fixture(); f.channel.setCredentialId(CREDENTIAL);
        assertThat(f.execute(operation)).isTrue();
        clearInvocations(f.network,f.http);
        when(f.config.loadBotCredentials()).thenReturn(List.of());
        assertThat(f.execute(operation)).isFalse();
        verifyNoInteractions(f.network,f.http);
    }

    @ParameterizedTest @MethodSource("sendOperations")
    void rereadsCredentialBeforeEachProviderCall(String operation) throws Exception {
        var f = new Fixture(); f.channel.setCredentialId(CREDENTIAL);
        // An early admission may be followed by catalog revocation before provider handoff.
        when(f.config.loadBotCredentials()).thenReturn(List.of(f.credential(true,"telegram","selected")),List.of());
        assertThat(f.execute(operation)).isFalse();
        verifyNoInteractions(f.network,f.http);
    }

    @Test void deletingCredentialKeepsExplicitBrokenBindingInsteadOfRevivingInlineToken() throws Exception {
        var f = new Fixture(); f.channel.setCredentialId(CREDENTIAL);
        when(f.channels.findAll()).thenReturn(List.of(f.channel));
        var response = f.service.deleteBotCredential(CREDENTIAL);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(f.channel.getCredentialId()).isEqualTo(CREDENTIAL);
        verify(f.channels,never()).saveAll(any());
        verify(f.channels,never()).save(any());
        when(f.config.loadBotCredentials()).thenReturn(List.of());
        for(String operation:OPERATIONS) assertThat(f.execute(operation)).as(operation).isFalse();
        verifyNoInteractions(f.network,f.http);
    }

    @Test void unknownConfiguredTargetCannotFallBackToSupportChat() throws Exception {
        var f = new Fixture();
        for(String target:java.util.Arrays.asList(null,"","unknown","group")) {
            assertThat(f.service.resolveConfiguredRecipient(ID,target,"777")).isNull();
            assertThat(f.service.sendConfiguredMessage(ID,target,"777","test").success()).isFalse();
        }
        verifyNoInteractions(f.network,f.http);
    }

    @Test void providerFailureDoesNotBecomeSuccessOrRetry() throws Exception {
        var f = new Fixture(); when(f.response.statusCode()).thenReturn(403);
        var result=f.service.sendConfiguredMessage(ID,"support_chat",null,"test");
        assertThat(result.success()).isFalse(); assertThat(result.error()).isEqualTo("delivery_failed");
        verify(f.http,times(1)).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
    }

    @Test void selectedCredentialRetainsMixedCasePlatformAndIgnoresNullUnrelatedEntries() throws Exception {
        var f = new Fixture(); f.channel.setCredentialId(CREDENTIAL); f.channel.setPlatform("TeLeGrAm");
        var catalog=new ArrayList<BotCredential>();catalog.add(null);catalog.add(f.credential(true,"TELEGRAM","selected"));
        when(f.config.loadBotCredentials()).thenReturn(catalog);
        assertThat(f.execute("support")).isTrue();
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(f.http).send(request.capture(),any(HttpResponse.BodyHandler.class));
        assertThat(request.getValue().uri().getPath()).isEqualTo("/botselected/sendMessage");
    }

    private static final class Fixture {
        final ChannelRepository channels=mock(ChannelRepository.class);
        final SharedConfigService config=mock(SharedConfigService.class);
        final IntegrationNetworkService network=mock(IntegrationNetworkService.class);
        final HttpClient http=mock(HttpClient.class);
        final HttpResponse<String> response=mock(HttpResponse.class);
        final Channel channel=new Channel();
        final ChannelTransportService service=new ChannelTransportService(channels,new ObjectMapper(),config,network);
        Fixture() throws Exception {
            channel.setId(ID);channel.setActive(true);channel.setPlatform("telegram");channel.setToken("old-inline");
            channel.setSupportChatId("-123");channel.setDeliverySettings("{\"broadcast_channel_id\":\"-456\"}");
            when(channels.findById(ID)).thenReturn(Optional.of(channel));
            when(channels.findTransportStateById(ID)).thenAnswer(call -> Optional.of(ChannelTransportPersistedStateTest.State.of(channel)));
            when(config.loadBotCredentials()).thenReturn(List.of(credential(true,"telegram","selected")));
            when(network.createChannelHttpClient(eq(channel),any())).thenReturn(http);
            when(network.resolveTelegramLegacyBotApiBaseUrl(channel)).thenReturn("https://api.telegram.org");
            when(response.statusCode()).thenReturn(200);when(response.body()).thenReturn("{\"ok\":true}");
            doReturn(response).when(http).send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class));
        }
        BotCredential credential(Boolean active,String platform,String token){return new BotCredential(CREDENTIAL,"selected",platform,token,active);}
        boolean execute(String operation) {
            if(operation.equals("test"))return Boolean.TRUE.equals(service.testChannel(ID,Map.of("target_mode","group","message","test")).getBody().get("success"));
            String target=switch(operation){case "support","resolve"->"support_chat";case "broadcast"->"broadcast_channel";case "custom"->"custom_chat";default->throw new AssertionError(operation);};
            String expected=switch(operation){case "support","resolve"->"-123";case "broadcast"->"-456";default->"777";};
            if(operation.equals("resolve"))return expected.equals(service.resolveConfiguredRecipient(ID,target,null));
            var result=service.sendConfiguredMessage(ID,target,"777","test");
            if(result.success())assertThat(result.recipient()).isEqualTo(expected);
            return result.success();
        }
    }
}
