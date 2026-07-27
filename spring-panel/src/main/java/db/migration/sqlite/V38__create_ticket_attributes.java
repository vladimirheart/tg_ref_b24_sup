package db.migration.sqlite;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V38__create_ticket_attributes extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS ticket_attributes (
                        ticket_id TEXT NOT NULL,
                        question_id TEXT NOT NULL,
                        attribute_key TEXT NOT NULL,
                        question_text TEXT,
                        input_type TEXT NOT NULL,
                        value_id TEXT,
                        value_label TEXT,
                        value_text TEXT,
                        include_in_dashboard BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TEXT,
                        updated_at TEXT,
                        PRIMARY KEY (ticket_id, question_id)
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ticket_attributes_ticket ON ticket_attributes(ticket_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ticket_attributes_key_dashboard ON ticket_attributes(attribute_key, include_in_dashboard)");
        }
    }
}
