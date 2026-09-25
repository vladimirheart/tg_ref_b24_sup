package com.example.supportbot.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ClientAvatarHistoryPostgresMappingContractTest {

    @Test
    void timestampColumnsBypassLegacyStringConverter() throws Exception {
        assertNativeOffsetDateTime("fetchedAt", "fetched_at");
        assertNativeOffsetDateTime("lastSeenAt", "last_seen_at");
    }

    private void assertNativeOffsetDateTime(String fieldName, String columnName) throws Exception {
        Field field = ClientAvatarHistory.class.getDeclaredField(fieldName);

        Column column = field.getAnnotation(Column.class);
        assertNotNull(column, fieldName + " must keep an explicit column mapping");
        assertEquals(columnName, column.name());

        Convert convert = field.getAnnotation(Convert.class);
        assertNotNull(convert, fieldName + " must explicitly override the legacy autoApply converter");
        assertTrue(convert.disableConversion(), fieldName + " must use Hibernate native OffsetDateTime JDBC binding");
    }
}
