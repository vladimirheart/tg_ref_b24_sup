package com.example.panel.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.support.PostgresqlJdbcTestSupport;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class LegacyTicketIdAdvisoryLockPostgresIntegrationTest {

    @Test
    void serializesTransactionsForTheSameExactLegacyTicketId() throws Exception {
        JdbcTemplate jdbc = PostgresqlJdbcTestSupport.freshJdbcTemplate("ticket_creation_lock");
        LegacyTicketIdConcurrencyGuard concurrencyGuard = new LegacyTicketIdConcurrencyGuard(jdbc);
        TransactionTemplate transactionTemplate = new TransactionTemplate(
            new DataSourceTransactionManager(jdbc.getDataSource())
        );
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstTransaction = new CountDownLatch(1);
        CountDownLatch secondStartsWaiting = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                concurrencyGuard.acquire("T-SAME-EXACT-ID");
                firstLockAcquired.countDown();
                await(releaseFirstTransaction);
            }));
            assertThat(firstLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                secondStartsWaiting.countDown();
                concurrencyGuard.acquire("T-SAME-EXACT-ID");
                secondLockAcquired.countDown();
            }));
            assertThat(secondStartsWaiting.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondLockAcquired.await(300, TimeUnit.MILLISECONDS)).isFalse();

            releaseFirstTransaction.countDown();
            first.get(5, TimeUnit.SECONDS);
            assertThat(secondLockAcquired.await(5, TimeUnit.SECONDS)).isTrue();
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirstTransaction.countDown();
            executor.shutdownNow();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out while waiting for advisory-lock transaction");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for advisory-lock transaction", ex);
        }
    }
}
