package com.example.panel.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ChannelPostgresMappingContractTest {

    @Test
    void channelTimestampsUseNativePostgresBinding() throws Exception {
        assertNativeOffsetDateTime("createdAt", "created_at");
        assertNativeOffsetDateTime("updatedAt", "updated_at");
    }

    private void assertNativeOffsetDateTime(String fieldName, String columnName) throws Exception {
        Field field = Channel.class.getDeclaredField(fieldName);

        Column column = field.getAnnotation(Column.class);
        assertNotNull(column, fieldName + " must keep an explicit PostgreSQL column mapping");
        assertEquals(columnName, column.name());

        Convert convert = field.getAnnotation(Convert.class);
        assertNull(convert, fieldName + " must use Hibernate native OffsetDateTime JDBC binding");
    }
}
