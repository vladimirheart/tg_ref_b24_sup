package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.supportbot.config.IntegrationRabbitProperties;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The listener boundary must reject foreign work before dispatcher/ledger/API/provider side effects. */
class FeedbackPromptChildRouteTest {
    private static final long BIG_CHANNEL = 9_007_199_254_740_993L;

    static Stream<Arguments> children() {
        return Stream.of("telegram", "vk", "max").flatMap(platform ->
            Stream.of(42L, BIG_CHANNEL).map(channel -> Arguments.of(platform, channel)));
    }

    static Stream<Arguments> invalidDeliveries() {
        return children().flatMap(child -> Stream.of(
            "null-event", "null-channel", "zero-channel", "negative-channel", "foreign-channel",
            "null-platform", "blank-platform", "foreign-platform", "unknown-platform", "uppercase-platform",
            "null-received-route", "blank-received-route", "foreign-received-route", "wildcard-received-route",
            "padded-received-route", "leading-zero-route", "null-configured-route", "blank-configured-route",
            "foreign-configured-route", "null-queue", "blank-queue", "foreign-queue", "wildcard-queue",
            "foreign-event-and-route"
        ).map(change -> Arguments.of(child.get()[0], child.get()[1], change)));
    }

    @ParameterizedTest @MethodSource("invalidDeliveries")
    void rejectsForeignOrUnresolvedChildDeliveryBeforeDispatch(String platform, long channel, String change) {
        var properties = properties(platform, channel);
        var dispatcher = mock(OutboundFeedbackPromptDispatchService.class);
        var event = event(platform, channel);
        String received = route(platform, channel);
        String foreignPlatform = platform.equals("telegram") ? "vk" : "telegram";
        switch (change) {
            case "null-event" -> event = null;
            case "null-channel" -> event = event(platform, null);
            case "zero-channel" -> event = event(platform, 0L);
            case "negative-channel" -> event = event(platform, -1L);
            case "foreign-channel" -> event = event(platform, channel + 1);
            case "null-platform" -> event = event(null, channel);
            case "blank-platform" -> event = event(" ", channel);
            case "foreign-platform" -> event = event(foreignPlatform, channel);
            case "unknown-platform" -> event = event("foreign", channel);
            case "uppercase-platform" -> event = event(platform.toUpperCase(java.util.Locale.ROOT), channel);
            case "null-received-route" -> received = null;
            case "blank-received-route" -> received = " ";
            case "foreign-received-route" -> received = route(platform, channel + 1);
            case "wildcard-received-route" -> received = "integration.outbound.feedback.prompt.*.channel.#";
            case "padded-received-route" -> received = " " + received + " ";
            case "leading-zero-route" -> {
                received = "integration.outbound.feedback.prompt." + platform + ".channel.0" + channel;
                properties.setOutboundRoutingKey(received);
            }
            case "null-configured-route" -> properties.setOutboundRoutingKey(null);
            case "blank-configured-route" -> properties.setOutboundRoutingKey(" ");
            case "foreign-configured-route" -> properties.setOutboundRoutingKey(route(platform, channel + 1));
            case "null-queue" -> properties.setOutboundQueue(null);
            case "blank-queue" -> properties.setOutboundQueue(" ");
            case "foreign-queue" -> properties.setOutboundQueue(queue(platform, channel + 1));
            case "wildcard-queue" -> properties.setOutboundQueue("iguana.integration.outbound.feedback-prompt.*.channel.#.bot");
            case "foreign-event-and-route" -> {
                event = event(platform, channel + 1);
                received = route(platform, channel + 1);
                properties.setOutboundRoutingKey(received);
            }
            default -> throw new AssertionError(change);
        }
        var listener = listener(dispatcher, properties);
        final var delivery = event;
        final var routingKey = received;
        assertThatThrownBy(() -> listener.onFeedbackPrompt(delivery, routingKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Outbound feedback prompt does not match this child runtime route.");
        verifyNoInteractions(dispatcher);
    }

    @ParameterizedTest @MethodSource("children")
    void exactChildRouteDispatchesTheOriginalEventOnce(String platform, long channel) {
        var dispatcher = mock(OutboundFeedbackPromptDispatchService.class);
        var event = event(platform, channel);
        listener(dispatcher, properties(platform, channel)).onFeedbackPrompt(event, route(platform, channel));
        verify(dispatcher).dispatch(same(event), eq(route(platform, channel)));
        verifyNoMoreInteractions(dispatcher);
    }

    @ParameterizedTest @MethodSource("children")
    void laterPropertyMutationCannotRebindTheRunningChild(String platform, long channel) {
        var dispatcher = mock(OutboundFeedbackPromptDispatchService.class);
        var properties = properties(platform, channel);
        var listener = listener(dispatcher, properties);
        properties.setOutboundRoutingKey(route(platform, channel + 1));
        properties.setOutboundQueue(queue(platform, channel + 1));
        var ownEvent = event(platform, channel);
        listener.onFeedbackPrompt(ownEvent, route(platform, channel));
        verify(dispatcher).dispatch(same(ownEvent), eq(route(platform, channel)));
        assertThatThrownBy(() -> listener.onFeedbackPrompt(event(platform, channel + 1), route(platform, channel + 1)))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoMoreInteractions(dispatcher);
    }

    @ParameterizedTest @MethodSource("children")
    void dispatcherFailureIsNotSwallowedOrRetriedByTheListener(String platform, long channel) {
        var dispatcher = mock(OutboundFeedbackPromptDispatchService.class);
        var event = event(platform, channel);
        var failure = new IllegalStateException("isolated dispatcher failure");
        doThrow(failure).when(dispatcher).dispatch(event, route(platform, channel));
        assertThatThrownBy(() -> listener(dispatcher, properties(platform, channel))
            .onFeedbackPrompt(event, route(platform, channel))).isSameAs(failure);
        verify(dispatcher).dispatch(event, route(platform, channel));
        verifyNoMoreInteractions(dispatcher);
    }

    // Baseline-only constructor compatibility: no behavior/assertion branch and no runtime bypass.
    private OutboundFeedbackPromptListener listener(OutboundFeedbackPromptDispatchService dispatcher,
                                                   IntegrationRabbitProperties properties) {
        try {
            try {
                return OutboundFeedbackPromptListener.class
                    .getConstructor(OutboundFeedbackPromptDispatchService.class, IntegrationRabbitProperties.class)
                    .newInstance(dispatcher, properties);
            } catch (NoSuchMethodException baseline) {
                return OutboundFeedbackPromptListener.class
                    .getConstructor(OutboundFeedbackPromptDispatchService.class).newInstance(dispatcher);
            }
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Listener construction failed", exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unexpected listener constructor", exception);
        }
    }

    private IntegrationRabbitProperties properties(String platform, long channel) {
        var properties = new IntegrationRabbitProperties();
        properties.setOutboundRoutingKey(route(platform, channel));
        properties.setOutboundQueue(queue(platform, channel));
        return properties;
    }

    private OutboundFeedbackPromptEvent event(String platform, Long channel) {
        return new OutboundFeedbackPromptEvent("evt-r251", "feedback.prompt.dispatch", "corr-r251",
            platform, channel, 100L, BIG_CHANNEL, " padded/Ю%?' ", "private prompt r251");
    }

    private String route(String platform, long channel) {
        return "integration.outbound.feedback.prompt." + platform + ".channel." + channel;
    }

    private String queue(String platform, long channel) {
        return "iguana.integration.outbound.feedback-prompt." + platform + ".channel." + channel + ".bot";
    }
}
