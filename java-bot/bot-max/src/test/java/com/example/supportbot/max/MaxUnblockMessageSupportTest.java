package com.example.supportbot.max;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.supportbot.entity.ClientUnblockRequest;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class MaxUnblockMessageSupportTest {

    @Test
    void operatorRequestMessagePreservesOptionalFieldsAndTimestampFormat() {
        ClientUnblockRequest request = request(42L, "100500", "Нужен доступ",
                OffsetDateTime.of(2026, 9, 17, 14, 5, 0, 0, java.time.ZoneOffset.UTC), "pending");

        assertThat(MaxUnblockMessageSupport.buildOperatorRequestMessage(request))
                .isEqualTo("Новый запрос на разблокировку\n"
                        + "Заявка: #42\n"
                        + "Клиент: 100500\n"
                        + "Причина: Нужен доступ\n"
                        + "Создан: 17.09.2026 14:05\n"
                        + "Статус: pending");
    }

    @Test
    void operatorRequestMessageOmitsMissingOptionalFields() {
        ClientUnblockRequest request = request(null, "100500", "   ", null, "pending");

        assertThat(MaxUnblockMessageSupport.buildOperatorRequestMessage(request))
                .isEqualTo("Новый запрос на разблокировку\nКлиент: 100500\nСтатус: pending");
    }

    @Test
    void createdClientResponsePreservesRequestNumberBehavior() {
        ClientUnblockRequest request = request(42L, "100500", null, null, "pending");

        assertThat(MaxUnblockMessageSupport.buildClientResponse(request, true, Duration.ZERO))
                .isEqualTo("Запрос на разблокировку отправлен оператору. Номер заявки: #42.");
        assertThat(MaxUnblockMessageSupport.buildClientResponse(null, true, Duration.ZERO))
                .isEqualTo("Запрос на разблокировку отправлен оператору.");
    }

    @Test
    void cooldownClientResponsePreservesLegacyMinuteRounding() {
        ClientUnblockRequest request = request(42L, "100500", null, null, "pending");

        assertThat(MaxUnblockMessageSupport.buildClientResponse(request, false, Duration.ofSeconds(60)))
                .isEqualTo("Запрос уже зарегистрирован под номером #42. Повторно можно отправить через менее минуты.");
        assertThat(MaxUnblockMessageSupport.buildClientResponse(request, false, Duration.ofSeconds(61)))
                .isEqualTo("Запрос уже зарегистрирован под номером #42. Повторно можно отправить через 2 мин..");
    }

    @Test
    void pendingClientResponsePreservesRequestNumberBehavior() {
        ClientUnblockRequest request = request(42L, "100500", null, null, "pending");

        assertThat(MaxUnblockMessageSupport.buildClientResponse(request, false, Duration.ZERO))
                .isEqualTo("Запрос уже на рассмотрении. Номер заявки: #42.");
        assertThat(MaxUnblockMessageSupport.buildClientResponse(null, false, Duration.ZERO))
                .isEqualTo("Запрос уже на рассмотрении.");
    }

    private ClientUnblockRequest request(Long id, String userId, String reason, OffsetDateTime createdAt, String status) {
        ClientUnblockRequest request = new ClientUnblockRequest();
        request.setId(id);
        request.setUserId(userId);
        request.setReason(reason);
        request.setCreatedAt(createdAt);
        request.setStatus(status);
        return request;
    }
}
