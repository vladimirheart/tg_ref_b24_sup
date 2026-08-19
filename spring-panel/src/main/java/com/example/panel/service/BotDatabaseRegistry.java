package com.example.panel.service;

import com.example.panel.config.BotSqliteDataSourceProperties;
import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.PanelDatabaseRuntimeMode;
import com.example.panel.config.SqliteConnectionConfigSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BotDatabaseRegistry {
    // Legacy local/dev helper for explicit per-channel bot SQLite shard bootstrap.
    // External PostgreSQL runtime must never rely on this service as schema owner.

    private static final Logger log = LoggerFactory.getLogger(BotDatabaseRegistry.class);

    private final BotProcessProperties botProcessProperties;
    private final BotSqliteDataSourceProperties botSqliteProperties;
    private final SqliteSchemaBootstrapSupport schemaBootstrapSupport;
    private final PanelDatabaseRuntimeMode databaseRuntimeMode;

    public BotDatabaseRegistry(BotProcessProperties botProcessProperties,
                               BotSqliteDataSourceProperties botSqliteProperties,
                               SqliteSchemaBootstrapSupport schemaBootstrapSupport,
                               PanelDatabaseRuntimeMode databaseRuntimeMode) {
        this.botProcessProperties = botProcessProperties;
        this.botSqliteProperties = botSqliteProperties;
        this.schemaBootstrapSupport = schemaBootstrapSupport;
        this.databaseRuntimeMode = databaseRuntimeMode;
    }

    public Path ensureBotDatabase(Long channelId, String platform) {
        Path dbPath = resolveBotDatabasePath(channelId);
        if (!databaseRuntimeMode.isSqliteMode()) {
            log.info("Skipping per-channel SQLite bot database bootstrap for channel {} in external {} mode", channelId, databaseRuntimeMode.modeLabel());
            return dbPath;
        }
        if (!botProcessProperties.isSqlitePerChannelShardEnabled()) {
            log.info("Skipping per-channel SQLite bot database bootstrap for channel {} because app.bots.sqlite-per-channel-shard-enabled=false", channelId);
            return dbPath;
        }
        ensureDatabaseFile(dbPath);
        ensureBotSchema(dbPath);
        log.info("Legacy per-channel bot shard database ready for channel {} (platform={}) at {}",
            channelId,
            platform,
            dbPath);
        return dbPath;
    }

    public Path resolveBotDatabasePath(Long channelId) {
        Path baseDir = botProcessProperties.resolveDatabaseDir();
        return baseDir.resolve("bot-" + channelId + ".db").toAbsolutePath().normalize();
    }

    private void ensureDatabaseFile(Path path) {
        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create bot database at " + path, ex);
        }
    }

    private void ensureBotSchema(Path dbPath) {
        DataSource dataSource = SqliteConnectionConfigSupport.createDataSource(
            botSqliteProperties.buildJdbcUrl(dbPath),
            botSqliteProperties.getJournalMode(),
            botSqliteProperties.getBusyTimeoutMs()
        );
        schemaBootstrapSupport.initializeSchema(dataSource, java.util.List.of(
            "CREATE TABLE IF NOT EXISTS bot_users (" +
                "user_id INTEGER PRIMARY KEY, " +
                "username TEXT, " +
                "first_name TEXT, " +
                "last_name TEXT, " +
                "registered_at TEXT" +
                ")",
            "CREATE TABLE IF NOT EXISTS bot_chat_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "message_id INTEGER, " +
                "ticket_id TEXT, " +
                "text TEXT, " +
                "message_type TEXT, " +
                "attachment_path TEXT, " +
                "timestamp TEXT, " +
                "FOREIGN KEY (user_id) REFERENCES bot_users(user_id)" +
                ")"
        ), dbPath.toString());
    }
}
