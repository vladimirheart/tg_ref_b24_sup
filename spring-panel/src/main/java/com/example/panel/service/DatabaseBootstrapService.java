package com.example.panel.service;

import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.ClientsSqliteDataSourceProperties;
import com.example.panel.config.KnowledgeSqliteDataSourceProperties;
import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteConnectionConfigSupport;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import javax.sql.DataSource;

@Service
public class DatabaseBootstrapService implements ApplicationRunner {
    // SQLite-only bootstrap for local secondary databases. Not part of the external PostgreSQL runtime path.

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrapService.class);

    private final BotSqliteDataSourceProperties botProperties;
    private final ClientsSqliteDataSourceProperties clientsProperties;
    private final KnowledgeSqliteDataSourceProperties knowledgeProperties;
    private final ObjectsSqliteDataSourceProperties objectsProperties;
    private final SqliteSchemaBootstrapSupport schemaBootstrapSupport;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;

    public DatabaseBootstrapService(BotSqliteDataSourceProperties botProperties,
                                    ClientsSqliteDataSourceProperties clientsProperties,
                                    KnowledgeSqliteDataSourceProperties knowledgeProperties,
                                    ObjectsSqliteDataSourceProperties objectsProperties,
                                    SqliteSchemaBootstrapSupport schemaBootstrapSupport,
                                    PanelDatabaseRuntimeMode databaseRuntimeMode) {
        this.botProperties = botProperties;
        this.clientsProperties = clientsProperties;
        this.knowledgeProperties = knowledgeProperties;
        this.objectsProperties = objectsProperties;
        this.schemaBootstrapSupport = schemaBootstrapSupport;
        this.databaseRuntimeMode = databaseRuntimeMode;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!databaseRuntimeMode.isSqliteMode()) {
            log.info("Skipping SQLite bootstrap because spring-panel runs in external {} mode", databaseRuntimeMode.modeLabel());
            return;
        }
        initializeClientsDatabase();
        initializeKnowledgeDatabase();
        initializeObjectsDatabase();
        initializeSharedBotRuntimeDatabase();
    }

    private void initializeClientsDatabase() {
        schemaBootstrapSupport.initializeSchema(createClientsCompatibilityDataSource(), List.of(
            "CREATE TABLE IF NOT EXISTS clients (" +
                "id INTEGER PRIMARY KEY, " +
                "platform TEXT, " +
                "display_name TEXT, " +
                "created_at TEXT" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_usernames (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "client_id INTEGER NOT NULL, " +
                "username TEXT, " +
                "seen_at TEXT, " +
                "FOREIGN KEY (client_id) REFERENCES clients(id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_phones (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "client_id INTEGER NOT NULL, " +
                "phone TEXT, " +
                "source TEXT, " +
                "created_at TEXT, " +
                "FOREIGN KEY (client_id) REFERENCES clients(id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_statuses (" +
                "client_id INTEGER PRIMARY KEY, " +
                "status TEXT, " +
                "updated_at TEXT, " +
                "FOREIGN KEY (client_id) REFERENCES clients(id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_blacklist (" +
                "client_id INTEGER PRIMARY KEY, " +
                "blocked_at TEXT, " +
                "blocked_by TEXT, " +
                "reason TEXT, " +
                "FOREIGN KEY (client_id) REFERENCES clients(id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_unblock_requests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "client_id INTEGER NOT NULL, " +
                "requested_at TEXT, " +
                "reason TEXT, " +
                "FOREIGN KEY (client_id) REFERENCES clients(id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_avatar_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "client_id INTEGER NOT NULL, " +
                "avatar_url TEXT, " +
                "last_seen_at TEXT, " +
                "FOREIGN KEY (client_id) REFERENCES clients(id)" +
                ")"
        ), "clients.db");
        log.info("Clients database ensured at {}", clientsProperties.getNormalizedPath());
    }

    private void initializeKnowledgeDatabase() {
        schemaBootstrapSupport.initializeSchema(createKnowledgeCompatibilityDataSource(), List.of(
            "CREATE TABLE IF NOT EXISTS knowledge_articles (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "title TEXT NOT NULL, " +
                "content TEXT, " +
                "created_at TEXT, " +
                "updated_at TEXT" +
                ")",
            "CREATE TABLE IF NOT EXISTS knowledge_article_files (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "article_id INTEGER NOT NULL, " +
                "file_name TEXT, " +
                "file_path TEXT, " +
                "created_at TEXT, " +
                "FOREIGN KEY (article_id) REFERENCES knowledge_articles(id)" +
                ")",
            "CREATE TABLE IF NOT EXISTS it_equipment_catalog (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "description TEXT, " +
                "created_at TEXT" +
                ")"
        ), "knowledge_base.db");
        log.info("Knowledge base database ensured at {}", knowledgeProperties.getNormalizedPath());
    }

    private void initializeObjectsDatabase() {
        schemaBootstrapSupport.initializeSchema(createObjectsCompatibilityDataSource(), List.of(
            "CREATE TABLE IF NOT EXISTS objects (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "address TEXT, " +
                "created_at TEXT" +
                ")",
            "CREATE TABLE IF NOT EXISTS object_passports (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "object_id INTEGER NOT NULL, " +
                "passport_number TEXT, " +
                "details TEXT, " +
                "created_at TEXT, " +
                "FOREIGN KEY (object_id) REFERENCES objects(id)" +
                ")"
        ), "objects.db");
        log.info("Objects database ensured at {}", objectsProperties.getNormalizedPath());
    }

    private void initializeSharedBotRuntimeDatabase() {
        schemaBootstrapSupport.initializeSchema(createBotRuntimeCompatibilityDataSource(), List.of(
            "CREATE TABLE IF NOT EXISTS feedbacks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER, " +
                "rating INTEGER, " +
                "timestamp TEXT, " +
                "ticket_id TEXT, " +
                "channel_id INTEGER" +
                ")",
            "CREATE TABLE IF NOT EXISTS client_unblock_requests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id TEXT NOT NULL, " +
                "channel_id INTEGER, " +
                "reason TEXT, " +
                "created_at TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'pending', " +
                "decided_at TEXT, " +
                "decided_by TEXT, " +
                "decision_comment TEXT" +
                ")",
            "CREATE INDEX IF NOT EXISTS idx_client_unblock_requests_user " +
                "ON client_unblock_requests(user_id)"
        ), "bot_runtime.db");
    }

    private DataSource createClientsCompatibilityDataSource() {
        return SqliteConnectionConfigSupport.createDataSource(clientsProperties);
    }

    private DataSource createKnowledgeCompatibilityDataSource() {
        return SqliteConnectionConfigSupport.createDataSource(knowledgeProperties);
    }

    private DataSource createObjectsCompatibilityDataSource() {
        return SqliteConnectionConfigSupport.createDataSource(objectsProperties);
    }

    private DataSource createBotRuntimeCompatibilityDataSource() {
        return SqliteConnectionConfigSupport.createDataSource(botProperties);
    }
}
