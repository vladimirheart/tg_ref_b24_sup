package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Feedback;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Repository wait is scripted; root SQL and the transaction use isolated PostgreSQL. */
class BotRuntimeFeedbackExpiryBoundaryPostgresTest {
    static Stream<Arguments> waits() {
        return Stream.of("BUSINESS", "GENERAL", "UNRESOLVED").flatMap(scope -> Stream.of("R241", " R241/?% Ю '")
                .flatMap(raw -> Stream.of(false, true).map(existing -> Arguments.of(scope, raw, existing))));
    }

    @ParameterizedTest(name = "{0}/raw={1}/existing={2}")
    @MethodSource("waits")
    void requestExpiringDuringRatingLookupDoesNotMutateRatingOrPublish(String scope, String raw, boolean hasExisting) {
        long owner = 9_007_199_254_740_993L;
        long channelId = 9_007_199_254_740_995L;
        var jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplateDefaultStringType("feedback_expiry_r241");
        jdbc.execute("CREATE TABLE tickets (ticket_id TEXT, user_id BIGINT, channel_id BIGINT, status TEXT, "
                + "ticket_public_id TEXT, business_id BIGINT, is_general BOOLEAN)");
        jdbc.update("INSERT INTO tickets VALUES (?, ?, ?, 'closed', ?, ?, ?)", raw, owner, channelId,
                "a".repeat(32), scope.equals("BUSINESS") ? 31L : null, scope.equals("GENERAL"));
        var before = jdbc.queryForList("SELECT * FROM tickets");
        var requests = mock(PendingFeedbackRequestRepository.class);
        var ratings = mock(FeedbackRepository.class);
        var events = mock(UiEventOutboxAppendService.class);
        var channel = new Channel();
        channel.setId(channelId);
        var pending = new PendingFeedbackRequest();
        pending.setId(1L);
        pending.setTicketId(raw);
        pending.setUserId(owner);
        pending.setChannel(channel);
        var existing = new Feedback();
        existing.setId(77L);
        existing.setTicketId(raw);
        existing.setUserId(owner);
        existing.setChannelId(channelId);
        existing.setRating(1);
        when(requests.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
        com.example.panel.support.PendingFeedbackContextTestSupport.stubLockedContextFromOwnerFixture(requests,jdbc);
        var entered = new AtomicBoolean();
        doAnswer(call -> {
            entered.set(true);
            long remaining = Duration.between(OffsetDateTime.now(), pending.getExpiresAt().plusNanos(30_000_000)).toMillis();
            if (remaining > 0) new CountDownLatch(1).await(remaining, TimeUnit.MILLISECONDS);
            return hasExisting ? Optional.of(existing) : Optional.empty();
        }).when(ratings).findFirstByTicketIdOrderByTimestampDesc(raw);
        var service = new BotRuntimeTicketWriteService(jdbc, mock(DialogReplyTargetService.class),
                mock(DialogResponsibilityService.class), mock(DialogParticipantService.class), events,
                mock(ProviderDeliveryLedgerService.class), requests, ratings, new LegacyTicketIdJdbcGuard(jdbc));
        var expiry = OffsetDateTime.now().plusSeconds(1);
        pending.setExpiresAt(expiry);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource()));

        var result = tx.execute(status -> service.storeFeedback(1L, 5));

        assertThat(entered).isTrue();
        assertThat(result).isEqualTo(new BotRuntimeTicketWriteService.MutationResult(false, true));
        assertThat(jdbc.queryForList("SELECT * FROM tickets")).isEqualTo(before);
        assertThat(pending.getExpiresAt()).isEqualTo(expiry);
        assertThat(existing.getRating()).isEqualTo(1);
        assertThat(existing.getTimestamp()).isNull();
        verify(ratings, never()).save(any());
        verify(requests, never()).save(any());
        verifyNoInteractions(events);
    }
}
