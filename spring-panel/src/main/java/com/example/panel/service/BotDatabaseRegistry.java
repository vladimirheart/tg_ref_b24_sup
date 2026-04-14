package com.example.panel.service;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.SettingsSqliteDataSourceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;
import org.springframework.stereotype.Service;

@Service
public class BotDatabaseRegistry {

    private static final Logger log = LoggerFactory.getLogger(BotDatabaseRegistry.class);

    private final SettingsSqliteDataSourceProperties settingsProperties;
    private final BotProcessProperties botProcessProperties;

    public BotDatabaseRegistry(SettingsSqliteDataSourceProperties settingsProperties,
                               BotProcessProperties botProcessProperties) {
        this.settingsProperties = settingsProperties;
        this.botProcessProperties = botProcessProperties;
    }

    public void ensureSettingsSchema() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(settingsProperties.buildJdbcUrl());
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE IF NOT EXISTS database_registry (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "db_type TEXT NOT NULL UNIQUE, " +
                "db_path TEXT NOT NULL, " +
                "updated_at TEXT" +
                ")");
            statement.execute("CREATE TABLE IF NOT EXISTS bot_instances (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "channel_id INTEGER NOT NULL UNIQUE, " +
                "bot_db_path TEXT NOT NULL, " +
                "platform TEXT, " +
                "created_at TEXT" +
                ")");
            statement.execute("CREATE TABLE IF NOT EXISTS database_links (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "source_type TEXT NOT NULL, " +
                "source_id TEXT NOT NULL, " +
                "target_type TEXT NOT NULL, " +
                "target_id TEXT NOT NULL, " +
                "created_at TEXT, " +
                "UNIQUE(source_type, source_id, target_type, target_id)" +
                ")");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize settings SQLite schema", ex);
        }
    }

    public void registerDatabase(String type, String path) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(settingsProperties.buildJdbcUrl());
        String sql = "INSERT INTO database_registry (db_type, db_path, updated_at) VALUES (?, ?, datetime('now')) " +
            "ON CONFLICT(db_type) DO UPDATE SET db_path = excluded.db_path, updated_at = excluded.updated_at";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, type);
            statement.setString(2, path);
            statement.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to register database " + type, ex);
        }
    }

    public void registerDatabaseLink(String sourceType, String sourceId, String targetType, String targetId) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(settingsProperties.buildJdbcUrl());
        String sql = "INSERT OR IGNORE INTO database_links " +
            "(source_type, source_id, target_type, target_id, created_at) " +
            "VALUES (?, ?, ?, ?, datetime('now'))";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sourceType);
            statement.setString(2, sourceId);
            statement.setString(3, targetType);
            statement.setString(4, targetId);
            statement.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to register database link " + sourceType + " -> " + targetType, ex);
        }
    }

    public Path ensureBotDatabase(Long channelId, String platform) {
        Path dbPath = resolveBotDatabasePath(channelId);
        ensureDatabaseFile(dbPath);
        ensureBotSchema(dbPath);
        registerBotInstance(channelId, platform, dbPath);
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
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbPath);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("CREATE TABLE IF NOT EXISTS bot_users (" +
                "user_id INTEGER PRIMARY KEY, " +
                "username TEXT, " +
                "first_name TEXT, " +
                "last_name TEXT, " +
                "registered_at TEXT" +
                ")");
            statement.execute("CREATE TABLE IF NOT EXISTS bot_chat_history (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER NOT NULL, " +
                "message_id INTEGER, " +
                "ticket_id TEXT, " +
                "text TEXT, " +
                "message_type TEXT, " +
                "attachment_path TEXT, " +
                "timestamp TEXT, " +
                "FOREIGN KEY (user_id) REFERENCES bot_users(user_id)" +
                ")");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize bot SQLite schema for " + dbPath, ex);
        }
    }

    private void registerBotInstance(Long channelId, String platform, Path dbPath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(settingsProperties.buildJdbcUrl());
        String sql = "INSERT INTO bot_instances (channel_id, bot_db_path, platform, created_at) VALUES (?, ?, ?, datetime('now')) " +
            "ON CONFLICT(channel_id) DO UPDATE SET bot_db_path = excluded.bot_db_path, platform = excluded.platform";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, channelId);
            statement.setString(2, dbPath.toString());
            statement.setString(3, platform);
            statement.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to register bot database for channel " + channelId, ex);
        }
        registerDatabaseLink("channel", Long.toString(channelId), "bot", dbPath.toString());
        log.info("Bot database ready for channel {} at {}", channelId, dbPath);
    }
}
