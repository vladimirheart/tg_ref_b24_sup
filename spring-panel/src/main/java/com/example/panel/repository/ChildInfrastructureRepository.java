package com.example.panel.repository;

import com.example.panel.security.ChildBotCredentials.Principal;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class ChildInfrastructureRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public ChildInfrastructureRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(new JdbcTransactionManager(jdbc.getDataSource()));
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setTimeout(45);
    }

    public State prepare(Principal child, String sqlPassword, String proposedRabbitHash) {
        // Commit SQL identity + retry journal before any external broker mutation.
        return transaction.execute(status -> {
            limits();
            return jdbc.queryForObject("SELECT managed,password_hash FROM bot_provision_child_transport(?,?,?,?)",
                (rs, row) -> new State(rs.getBoolean("managed"), rs.getString("password_hash")),
                child.channelId(), child.platform(), sqlPassword, proposedRabbitHash);
        });
    }

    public void complete(Principal child, Consumer<String> provisionBroker) {
        transaction.executeWithoutResult(status -> {
            limits();
            // Also blocks deletion, platform changes and disabling during broker setup.
            Boolean active = jdbc.queryForObject("""
                SELECT c.is_active AND c.platform=? AND p.platform=c.platform AND p.enabled
                FROM channels c JOIN bot_child_principals p ON p.channel_id=c.id
                WHERE c.id=? AND p.role_name=? FOR SHARE OF c,p
                """, Boolean.class, child.platform(), child.channelId(), child.databaseRole());
            if (!Boolean.TRUE.equals(active)) throw new IllegalStateException("Child channel changed");
            String hash = jdbc.queryForObject("SELECT rabbit_password_hash FROM bot_child_infrastructure WHERE channel_id=? AND platform=? FOR UPDATE",
                String.class, child.channelId(), child.platform());
            provisionBroker.accept(hash);
            jdbc.update("UPDATE bot_child_infrastructure SET ready_at=now() WHERE channel_id=?", child.channelId());
        });
    }

    private void limits() {
        jdbc.execute("SET LOCAL lock_timeout='5s'");
        jdbc.execute("SET LOCAL statement_timeout='10s'");
    }

    public record State(boolean managed, String rabbitHash) {}
}
