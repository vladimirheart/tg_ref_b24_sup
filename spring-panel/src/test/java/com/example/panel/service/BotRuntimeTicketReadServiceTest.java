package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.Message;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.TicketActive;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import java.util.Optional;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class BotRuntimeTicketReadServiceTest {

    @Test
    void findTicketUsesFailClosedUniqueLegacyResolver() {
        TicketRepository ticketRepository = mock(TicketRepository.class);
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            mock(MessageRepository.class),
            ticketRepository,
            mock(TicketActiveRepository.class),
            mock(FeedbackRepository.class),
            mock(PendingFeedbackRequestRepository.class)
        );
        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setUserId(901L);
        ticketId.setTicketId(" T-901 ");
        ticket.setId(ticketId);
        ticket.setStatus("open");
        when(ticketRepository.findUniqueByLegacyTicketId(" T-901 ")).thenReturn(Optional.of(ticket));

        Optional<BotRuntimeTicketReadService.TicketLookup> result = service.findTicket(" T-901 ");

        assertThat(result).contains(new BotRuntimeTicketReadService.TicketLookup(901L, " T-901 ", "open"));
        verify(ticketRepository).findUniqueByLegacyTicketId(" T-901 ");
    }

    @ParameterizedTest
    @NullSource
    @EmptySource
    @ValueSource(strings = {" ", "T-MISSING", "T-DUPLICATE", " T-901 "})
    void requestNumberSkipsUnresolvedTicketBeforeReadingRootMessage(String ticketId) {
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            messageRepository, ticketRepository, mock(TicketActiveRepository.class),
            mock(FeedbackRepository.class), mock(PendingFeedbackRequestRepository.class)
        );

        assertThat(service.resolveRequestNumber(ticketId)).isEmpty();

        verifyNoInteractions(messageRepository);
    }

    @Test
    void requestNumberPreservesExactStoredLegacyId() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        Ticket ticket = new Ticket();
        TicketId id = new TicketId();
        id.setUserId(901L);
        id.setTicketId(" T-901 ");
        ticket.setId(id);
        when(ticketRepository.findUniqueByLegacyTicketId(" T-901 ")).thenReturn(Optional.of(ticket));
        Message message = new Message();
        message.setUserId(901L);
        message.setTicketId(" T-901 ");
        when(messageRepository.findByTicketId(" T-901 ")).thenReturn(Optional.of(message));
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            messageRepository, ticketRepository, mock(TicketActiveRepository.class),
            mock(FeedbackRepository.class), mock(PendingFeedbackRequestRepository.class)
        );

        assertThat(service.resolveRequestNumber(" T-901 "))
            .contains(new BotRuntimeTicketReadService.RequestNumberLookup(" T-901 ", " T-901 "));
        verify(messageRepository).findByTicketId(" T-901 ");
    }

    @Test
    void requestNumberDoesNotExposeRootMessageBelongingToAnotherUser() {
        MessageRepository messageRepository = mock(MessageRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        Ticket ticket = new Ticket();
        TicketId id = new TicketId();
        id.setUserId(901L);
        id.setTicketId("T-901");
        ticket.setId(id);
        when(ticketRepository.findUniqueByLegacyTicketId("T-901")).thenReturn(Optional.of(ticket));
        Message message = new Message();
        message.setUserId(902L);
        message.setTicketId("T-901");
        when(messageRepository.findByTicketId("T-901")).thenReturn(Optional.of(message));
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            messageRepository, ticketRepository, mock(TicketActiveRepository.class),
            mock(FeedbackRepository.class), mock(PendingFeedbackRequestRepository.class)
        );

        assertThat(service.resolveRequestNumber("T-901")).isEmpty();
    }

    @Test
    void findActiveFeedbackRequestReturnsChannelScopedRequestWhenNotRatedYet() {
        TicketRepository ticketRepository = mock(TicketRepository.class);
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            mock(MessageRepository.class),
            ticketRepository,
            mock(TicketActiveRepository.class),
            feedbackRepository,
            pendingRepository
        );

        Channel channel = new Channel();
        channel.setId(19L);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(501L);
        request.setUserId(77L);
        request.setTicketId("T-501");
        request.setSource("auto_close");
        request.setChannel(channel);

        when(ticketRepository.findUniqueByLegacyTicketId("T-501")).thenReturn(Optional.of(ticket("T-501", 77L, 19L)));
        when(pendingRepository.findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(eq(77L), eq(19L), any()))
            .thenReturn(Optional.of(request));
        when(feedbackRepository.existsByTicketId("T-501")).thenReturn(false);

        Optional<BotRuntimeTicketReadService.PendingFeedbackRequestLookup> result = service.findActiveFeedbackRequest(77L, 19L);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(501L);
        assertThat(result.orElseThrow().channelId()).isEqualTo(19L);
        assertThat(result.orElseThrow().ticketId()).isEqualTo("T-501");
    }

    @Test
    void findActiveFeedbackRequestFallsBackAndSkipsAlreadyRatedRequest() {
        TicketRepository ticketRepository = mock(TicketRepository.class);
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            mock(MessageRepository.class),
            ticketRepository,
            mock(TicketActiveRepository.class),
            feedbackRepository,
            pendingRepository
        );

        Channel channel = new Channel();
        channel.setId(12L);

        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setId(500L);
        request.setUserId(42L);
        request.setTicketId("T-500");
        request.setSource("user_prompt");
        request.setChannel(channel);

        when(ticketRepository.findUniqueByLegacyTicketId("T-500")).thenReturn(Optional.of(ticket("T-500", 42L, 12L)));
        when(pendingRepository.findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), eq(12L), any()))
            .thenReturn(Optional.of(request));
        when(feedbackRepository.existsByTicketId("T-500")).thenReturn(true);
        when(pendingRepository.findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), any()))
            .thenReturn(Optional.of(request));

        Optional<BotRuntimeTicketReadService.PendingFeedbackRequestLookup> result = service.findActiveFeedbackRequest(42L, 12L);

        assertThat(result).isEmpty();
        verify(pendingRepository).findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), eq(12L), any());
        verify(pendingRepository).findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(42L), any());
    }
    @Test
    void activeTicketSkipsUnresolvedAndMismatchedCandidatesBeforeValidExactId() {
        ReadFixture fixture = new ReadFixture();
        when(fixture.tickets.findUniqueByLegacyTicketId("wrong-owner"))
            .thenReturn(Optional.of(ticket("wrong-owner", 88L, 9L)));
        when(fixture.tickets.findUniqueByLegacyTicketId("wrong-channel"))
            .thenReturn(Optional.of(ticket("wrong-channel", 77L, 10L)));
        when(fixture.tickets.findUniqueByLegacyTicketId(" exact "))
            .thenReturn(Optional.of(ticket(" exact ", 77L, 9L)));
        when(fixture.active.findByUserIdentityInOrderByLastSeenDescAndChannelId(List.of("77"), 9L))
            .thenReturn(List.of(active("missing"), active("duplicate"), active("wrong-owner"),
                active("wrong-channel"), active(" exact ")));

        assertThat(fixture.service.findActiveTicket(77L, null, 9L))
            .get().extracting(BotRuntimeTicketReadService.ActiveTicketLookup::ticketId).isEqualTo(" exact ");
    }

    @Test
    void usernameOnlyActiveLookupStillRequiresUniqueCanonicalTicket() {
        ReadFixture fixture = new ReadFixture();
        when(fixture.active.findByUserIdentityInOrderByLastSeenDesc(List.of("client")))
            .thenReturn(List.of(active("duplicate")));

        assertThat(fixture.service.findActiveTicket(null, "client", null)).isEmpty();
        verify(fixture.tickets).findUniqueByLegacyTicketId("duplicate");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "wrong-owner", "wrong-message-owner"})
    void lastContextDoesNotExposeInvalidChildReference(String condition) {
        ReadFixture fixture = new ReadFixture();
        Message message = message("T-LAST", "wrong-message-owner".equals(condition) ? 88L : 77L);
        when(fixture.messages.findTopByUserIdOrderByCreatedAtDesc(77L)).thenReturn(Optional.of(message));
        if ("wrong-owner".equals(condition)) {
            when(fixture.tickets.findUniqueByLegacyTicketId("T-LAST"))
                .thenReturn(Optional.of(ticket("T-LAST", 88L, 9L)));
        }

        assertThat(fixture.service.findLastTicketContext(77L)).isEmpty();
    }

    @Test
    void recentTicketsFilterInvalidRootsBeforeLimitAndDoNotExposeOtherUsersRating() {
        ReadFixture fixture = new ReadFixture();
        when(fixture.messages.findTop10ByUserIdOrderByCreatedAtDesc(77L))
            .thenReturn(List.of(message("duplicate", 77L), message("wrong-owner", 77L),
                message("wrong-child", 88L), message(" exact ", 77L), message("later", 77L)));
        when(fixture.tickets.findUniqueByLegacyTicketId("wrong-owner"))
            .thenReturn(Optional.of(ticket("wrong-owner", 88L, 9L)));
        when(fixture.tickets.findUniqueByLegacyTicketId(" exact "))
            .thenReturn(Optional.of(ticket(" exact ", 77L, 9L)));
        Feedback foreign = new Feedback();
        foreign.setTicketId(" exact ");
        foreign.setUserId(88L);
        foreign.setRating(1);
        when(fixture.feedback.findByTicketIdIn(List.of(" exact "))).thenReturn(List.of(foreign));

        assertThat(fixture.service.findRecentTickets(77L, 1))
            .containsExactly(new BotRuntimeTicketReadService.TicketSummaryLookup(
                " exact ", " exact ", null, null, null, null, null, null, null));
        verify(fixture.feedback).findByTicketIdIn(List.of(" exact "));
    }

    @Test
    void recentTicketsWithNoValidRootsDoNotReadFeedback() {
        ReadFixture fixture = new ReadFixture();
        when(fixture.messages.findTop10ByUserIdOrderByCreatedAtDesc(77L))
            .thenReturn(List.of(message("duplicate", 77L)));

        assertThat(fixture.service.findRecentTickets(77L, 10)).isEmpty();
        verifyNoInteractions(fixture.feedback);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "wrong-owner", "wrong-channel", "null-channel", "wrong-user"})
    void pendingFeedbackRejectsUnresolvedOrMismatchedRequestBeforeReadingRating(String condition) {
        ReadFixture fixture = new ReadFixture();
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setTicketId("T-FEEDBACK");
        request.setUserId("wrong-user".equals(condition) ? 88L : 77L);
        if (!"null-channel".equals(condition)) {
            Channel channel = new Channel();
            channel.setId(9L);
            request.setChannel(channel);
        }
        if (!List.of("missing", "duplicate").contains(condition)) {
            when(fixture.tickets.findUniqueByLegacyTicketId("T-FEEDBACK")).thenReturn(Optional.of(ticket(
                "T-FEEDBACK", "wrong-owner".equals(condition) ? 88L : 77L,
                "wrong-channel".equals(condition) ? 10L : 9L)));
        }
        when(fixture.pending.findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(eq(77L), eq(9L), any()))
            .thenReturn(Optional.of(request));
        when(fixture.pending.findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(77L), any()))
            .thenReturn(Optional.of(request));

        assertThat(fixture.service.findActiveFeedbackRequest(77L, 9L)).isEmpty();
        verifyNoInteractions(fixture.feedback);
    }

    @Test
    void pendingFeedbackFallbackCannotCrossRequestedChannel() {
        ReadFixture fixture = new ReadFixture();
        Channel channel = new Channel();
        channel.setId(10L);
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setUserId(77L);
        request.setChannel(channel);
        request.setTicketId("T-OTHER-CHANNEL");
        when(fixture.pending.findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(77L), any()))
            .thenReturn(Optional.of(request));

        assertThat(fixture.service.findActiveFeedbackRequest(77L, 9L)).isEmpty();
        verifyNoInteractions(fixture.feedback);
    }

    @Test
    void lastContextAndRecentRatingStillWorkForCanonicalTicket() {
        ReadFixture fixture = new ReadFixture();
        Message message = message("T-VALID", 77L);
        message.setProblem("printer");
        when(fixture.tickets.findUniqueByLegacyTicketId("T-VALID"))
            .thenReturn(Optional.of(ticket("T-VALID", 77L, 9L)));
        when(fixture.messages.findTopByUserIdOrderByCreatedAtDesc(77L)).thenReturn(Optional.of(message));
        when(fixture.messages.findTop10ByUserIdOrderByCreatedAtDesc(77L)).thenReturn(List.of(message));
        Feedback feedback = new Feedback();
        feedback.setUserId(77L);
        feedback.setTicketId("T-VALID");
        feedback.setRating(5);
        when(fixture.feedback.findByTicketIdIn(List.of("T-VALID"))).thenReturn(List.of(feedback));

        assertThat(fixture.service.findLastTicketContext(77L)).get()
            .extracting(BotRuntimeTicketReadService.LastTicketContext::problem).isEqualTo("printer");
        assertThat(fixture.service.findRecentTickets(77L, 10)).singleElement()
            .extracting(BotRuntimeTicketReadService.TicketSummaryLookup::rating).isEqualTo(5);
    }

    private static Ticket ticket(String legacyId, Long userId, Long channelId) {
        TicketId id = new TicketId();
        id.setTicketId(legacyId);
        id.setUserId(userId);
        Ticket ticket = new Ticket();
        ticket.setId(id);
        Channel channel = new Channel();
        channel.setId(channelId);
        ticket.setChannel(channel);
        return ticket;
    }

    private static Message message(String ticketId, Long userId) {
        Message message = new Message();
        message.setTicketId(ticketId);
        message.setUserId(userId);
        return message;
    }

    private static TicketActive active(String ticketId) {
        TicketActive active = new TicketActive();
        active.setTicketId(ticketId);
        active.setUserIdentity("77");
        return active;
    }

    private static final class ReadFixture {
        private final MessageRepository messages = mock(MessageRepository.class);
        private final TicketRepository tickets = mock(TicketRepository.class);
        private final TicketActiveRepository active = mock(TicketActiveRepository.class);
        private final FeedbackRepository feedback = mock(FeedbackRepository.class);
        private final PendingFeedbackRequestRepository pending = mock(PendingFeedbackRequestRepository.class);
        private final BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            messages, tickets, active, feedback, pending);
    }
}
