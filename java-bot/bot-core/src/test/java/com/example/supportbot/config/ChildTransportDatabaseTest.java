package com.example.supportbot.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class ChildTransportDatabaseTest {
    private MockEnvironment environment() {return new MockEnvironment()
        .withProperty("app.integration.transport.mode","rabbitmq").withProperty("app.bot-runtime.channel-id","22")
        .withProperty("app.bot-runtime.platform","max").withProperty("app.integration.panel-api.token","iguana-child-v1:max:22:"+"a".repeat(64));}
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9,10})
    void unsafeDatabaseIdentityFailsBeforeAnyRepositoryStarts(int check) throws Exception {
        var source=mock(DataSource.class);var connection=mock(Connection.class);var query=mock(PreparedStatement.class);var result=mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getBoolean(anyInt())).thenAnswer(call -> (int)call.getArgument(0)!=check);
        assertThatThrownBy(()->ChildTransportDatabase.validate(source,environment())).isInstanceOf(IllegalStateException.class).hasMessageContaining("check "+check);
    }
    @ParameterizedTest @ValueSource(strings={"shared-token","iguana-child-v1:max:23:","iguana-child-v1:vk:22:","iguana-child-v1:MAX:22:"})
    void missingOrForeignApiPrincipalNeverOpensDatabaseConnection(String token) {
        var source=mock(DataSource.class);var environment=environment().withProperty("app.integration.panel-api.token",token+"a".repeat(64));
        assertThatThrownBy(()->ChildTransportDatabase.validate(source,environment)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(source);
    }
    @Test void correctlyScopedRolePassesAllStartupChecks() throws Exception {
        var source=mock(DataSource.class);var connection=mock(Connection.class);var query=mock(PreparedStatement.class);var result=mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection);when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(result);when(result.next()).thenReturn(true);when(result.getBoolean(anyInt())).thenReturn(true);
        assertThatCode(()->ChildTransportDatabase.validate(source,environment())).doesNotThrowAnyException();
        verify(query).setString(1,"iguana_child_22");verify(query).setLong(2,22L);verify(query).setString(3,"max");
    }
}
