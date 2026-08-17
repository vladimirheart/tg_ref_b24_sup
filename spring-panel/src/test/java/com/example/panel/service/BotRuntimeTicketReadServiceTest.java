package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotRuntimeTicketReadServiceTest {

    @Test
    void findActiveFeedbackRequestReturnsChannelScopedRequestWhenNotRatedYet() {
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            mock(MessageRepository.class),
            mock(TicketRepository.class),
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
        PendingFeedbackRequestRepository pendingRepository = mock(PendingFeedbackRequestRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        BotRuntimeTicketReadService service = new BotRuntimeTicketReadService(
            mock(MessageRepository.class),
            mock(TicketRepository.class),
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
}
