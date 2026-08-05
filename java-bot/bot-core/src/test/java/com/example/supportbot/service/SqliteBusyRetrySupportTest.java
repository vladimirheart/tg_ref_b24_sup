package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;

class SqliteBusyRetrySupportTest {

    @Test
    void retriesBusyExceptionUntilSuccess() {
        AtomicInteger attempts = new AtomicInteger();

        String result = SqliteBusyRetrySupport.get(() -> {
            if (attempts.getAndIncrement() < 2) {
                throw new CannotAcquireLockException("[SQLITE_BUSY] database is locked");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(3);
    }

    @Test
    void doesNotRetryNonBusyDataAccessException() {
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> SqliteBusyRetrySupport.get(() -> {
            attempts.incrementAndGet();
            throw new DataAccessResourceFailureException("network down");
        }))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(attempts.get()).isEqualTo(1);
    }
}
