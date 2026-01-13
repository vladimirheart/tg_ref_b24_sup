package com.example.panel.service;

import com.example.panel.config.BotProcessProperties;
import com.example.panel.config.ClientsSqliteDataSourceProperties;
import com.example.panel.config.KnowledgeSqliteDataSourceProperties;
import com.example.panel.config.ObjectsSqliteDataSourceProperties;
import com.example.panel.config.SettingsSqliteDataSourceProperties;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteDataSource;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class DatabaseBootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrapService.class);

    private final ClientsSqliteDataSourceProperties clientsProperties;
    private final KnowledgeSqliteDataSourceProperties knowledgeProperties;
    private final ObjectsSqliteDataSourceProperties objectsProperties;
    private final SettingsSqliteDataSourceProperties settingsProperties;
    private final ChannelRepository channelRepository;
    private final BotDatabaseRegistry botDatabaseRegistry;
    private final BotProcessProperties botProcessProperties;

    public DatabaseBootstrapService(ClientsSqliteDataSourceProperties clientsProperties,
                                    KnowledgeSqliteDataSourceProperties knowledgeProperties,
                                    ObjectsSqliteDataSourceProperties objectsProperties,
                                    SettingsSqliteDataSourceProperties settingsProperties,
                                    ChannelRepository channelRepository,
                                    BotDatabaseRegistry botDatabaseRegistry,
                                    BotProcessProperties botProcessProperties) {
        this.clientsProperties = clientsProperties;
        this.knowledgeProperties = knowledgeProperties;
        this.objectsProperties = objectsProperties;
        this.settingsProperties = settingsProperties;
        this.channelRepository = channelRepository;
        this.botDatabaseRegistry = botDatabaseRegistry;
        this.botProcessProperties = botProcessProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        initializeClientsDatabase();
        initializeKnowledgeDatabase();
        initializeObjectsDatabase();
        botDatabaseRegistry.ensureSettingsSchema();
        registerDatabaseLinks();
        initializeBotDatabases();
    }

    private void initializeClientsDatabase() {
        initializeSchema(clientsProperties.buildJdbcUrl(), List.of(
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
        ));
        log.info("Clients database ensured at {}", clientsProperties.getNormalizedPath());
    }

    private void initializeKnowledgeDatabase() {
        initializeSchema(knowledgeProperties.buildJdbcUrl(), List.of(
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
        ));
        log.info("Knowledge base database ensured at {}", knowledgeProperties.getNormalizedPath());
    }

    private void initializeObjectsDatabase() {
        initializeSchema(objectsProperties.buildJdbcUrl(), List.of(
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
        ));
        log.info("Objects database ensured at {}", objectsProperties.getNormalizedPath());
    }

    private void registerDatabaseLinks() {
        botDatabaseRegistry.registerDatabase("clients", clientsProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabase("knowledge", knowledgeProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabase("objects", objectsProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabase("settings", settingsProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabase("bots", botProcessProperties.resolveDatabaseDir().toString());

        botDatabaseRegistry.registerDatabaseLink("settings", "global", "clients", clientsProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabaseLink("settings", "global", "knowledge", knowledgeProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabaseLink("settings", "global", "objects", objectsProperties.getNormalizedPath().toString());
        botDatabaseRegistry.registerDatabaseLink("settings", "global", "bots", botProcessProperties.resolveDatabaseDir().toString());
    }

    private void initializeBotDatabases() {
        List<Channel> channels = channelRepository.findAll();
        for (Channel channel : channels) {
            botDatabaseRegistry.ensureBotDatabase(channel.getId(), channel.getPlatform());
        }
    }

    private void initializeSchema(String jdbcUrl, List<String> statements) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(jdbcUrl);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys=ON");
            for (String sql : statements) {
                statement.execute(sql);
            }
            statement.execute("CREATE TABLE IF NOT EXISTS schema_audit (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "applied_at TEXT NOT NULL" +
                ")");
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize SQLite schema for " + jdbcUrl, ex);
        }
    }
}
