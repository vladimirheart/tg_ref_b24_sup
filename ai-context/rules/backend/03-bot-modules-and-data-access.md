# Bot модули и доступ к данным: Правила проекта Iguana

## Правило 1: Bot Adapter Pattern для многоплатформности

### Проблема:

Каждый bot-модуль (Telegram, VK, MAX) использует core сервисы напрямую. Это создает плотную связанность и делает невозможным переиспользование кода.

```java
// ❌ Неправильно: Плотная связанность
@Component
public class SupportBot extends TelegramLongPollingBot {
    private final TicketService ticketService;
    private final FeedbackService feedbackService;
    
    public void onUpdateReceived(Update update) {
        ticketService.create(...);  // ← core service напрямую
    }
}
```

### Решение: Adapter Pattern

#### 1. Создать интерфейсы в bot-core:

```java
// java-bot/bot-core/src/main/java/com/example/supportbot/api/adapter/IBotPlatformAdapter.java
package com.example.supportbot.api.adapter;

import java.util.concurrent.CompletableFuture;

/**
 * Интерфейс для адаптера платформы (Telegram, VK, MAX)
 * Позволяет абстрагировать платформ-специфичный код
 */
public interface IBotPlatformAdapter {
    
    /**
     * Отправить простое текстовое сообщение
     */
    CompletableFuture<Void> sendMessage(String chatId, String text);
    
    /**
     * Отправить HTML-форматированное сообщение
     */
    CompletableFuture<Void> sendFormattedMessage(String chatId, String html);
    
    /**
     * Отправить медиа (фото, видео, документ)
     */
    CompletableFuture<Void> sendMedia(String chatId, MediaAttachment media);
    
    /**
     * Получить профиль пользователя в платформе
     */
    CompletableFuture<UserProfile> getUserProfile(String userId);
    
    /**
     * Получить информацию о сообщении
     */
    CompletableFuture<MessageInfo> getMessageInfo(String messageId);
}

// java-bot/bot-core/src/main/java/com/example/supportbot/api/adapter/IInboundMessageHandler.java
package com.example.supportbot.api.adapter;

import java.util.concurrent.CompletableFuture;

/**
 * Интерфейс для обработки входящих сообщений от платформы
 */
public interface IInboundMessageHandler {
    
    /**
     * Обработать входящее сообщение
     */
    CompletableFuture<Void> handleInboundMessage(InboundMessage message);
    
    /**
     * Обработать callback запрос (для VK, MAX)
     */
    CompletableFuture<Void> handleCallback(CallbackRequest request);
    
    /**
     * Обработать файл/вложение
     */
    CompletableFuture<Void> handleAttachment(AttachmentInfo attachment);
}

// java-bot/bot-core/src/main/java/com/example/supportbot/api/model/InboundMessage.java
public record InboundMessage(
    String messageId,
    String chatId,
    String userId,
    String userName,
    String text,
    Long timestamp,
    List<AttachmentInfo> attachments
) {}

public record UserProfile(
    String userId,
    String firstName,
    String lastName,
    String phoneNumber,
    String email
) {}

public record MediaAttachment(
    String type,  // "photo", "video", "document"
    String url,
    String fileName,
    Long fileSize
) {}

public record CallbackRequest(
    String callbackId,
    String chatId,
    String payload,
    Long timestamp
) {}

public record AttachmentInfo(
    String fileId,
    String fileName,
    String mimeType,
    Long fileSize,
    String url
) {}

public record MessageInfo(
    String messageId,
    String text,
    Long timestamp,
    String senderId
) {}
```

#### 2. Реализовать адаптер для Telegram:

```java
// java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/adapter/TelegramBotAdapter.java
package com.example.supportbot.telegram.adapter;

import com.example.supportbot.api.adapter.*;
import com.example.supportbot.api.model.*;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.concurrent.CompletableFuture;

@Component
public class TelegramBotAdapter extends TelegramLongPollingBot implements IBotPlatformAdapter {
    
    private final IInboundMessageHandler messageHandler;
    
    public TelegramBotAdapter(IInboundMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }
    
    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String text) {
        return CompletableFuture.runAsync(() -> {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(text);
            
            try {
                execute(message);
            } catch (TelegramApiException ex) {
                throw new RuntimeException("Failed to send Telegram message", ex);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> sendFormattedMessage(String chatId, String html) {
        return CompletableFuture.runAsync(() -> {
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(html);
            message.setParseMode("HTML");
            
            try {
                execute(message);
            } catch (TelegramApiException ex) {
                throw new RuntimeException("Failed to send Telegram formatted message", ex);
            }
        });
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            var msg = update.getMessage();
            
            InboundMessage inbound = new InboundMessage(
                String.valueOf(msg.getMessageId()),
                String.valueOf(msg.getChatId()),
                String.valueOf(msg.getFrom().getId()),
                msg.getFrom().getFirstName(),
                msg.getText(),
                msg.getDate() * 1000L,
                List.of()  // Упрощенно
            );
            
            messageHandler.handleInboundMessage(inbound);
        }
    }
    
    // Другие методы интерфейса...
}
```

#### 3. Реализовать адаптер для VK:

```java
// java-bot/bot-vk/src/main/java/com/example/supportbot/vk/adapter/VkBotAdapter.java
package com.example.supportbot.vk.adapter;

import com.example.supportbot.api.adapter.*;
import com.example.supportbot.api.model.*;
import com.vk.api.sdk.objects.messages.Message;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class VkBotAdapter implements IBotPlatformAdapter {
    
    private final IInboundMessageHandler messageHandler;
    private final VkApiClient vkApiClient;
    
    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String text) {
        return CompletableFuture.runAsync(() -> {
            vkApiClient.messages()
                .send(Long.parseLong(chatId), text)
                .execute();
        });
    }
    
    @Override
    public CompletableFuture<Void> sendFormattedMessage(String chatId, String html) {
        // VK имеет другой формат, может быть нужна конвертация
        return sendMessage(chatId, html.replaceAll("<[^>]*>", ""));
    }
    
    // Другие методы...
}
```

#### 4. Реализовать обработчик сообщений:

```java
// java-bot/bot-core/src/main/java/com/example/supportbot/service/InboundMessageHandlerImpl.java
package com.example.supportbot.service;

import com.example.supportbot.api.adapter.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

@Service
public class InboundMessageHandlerImpl implements IInboundMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(InboundMessageHandlerImpl.class);
    
    private final ITicketService ticketService;
    private final IChatHistoryService chatHistoryService;
    private final IBotPlatformAdapter botAdapter;
    
    @Override
    public CompletableFuture<Void> handleInboundMessage(InboundMessage message) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.info("Processing message from user: {} in chat: {}", 
                    message.userId(), message.chatId());
                
                // Бизнес-логика независимая от платформы
                ticketService.processIncomingMessage(
                    message.chatId(),
                    message.userId(),
                    message.text()
                );
                
                // Отправить подтверждение (через адаптер, не зависит от платформы)
                botAdapter.sendMessage(message.chatId(), "Спасибо за сообщение!");
                
            } catch (Exception ex) {
                log.error("Failed to process message", ex);
            }
        });
    }
    
    @Override
    public CompletableFuture<Void> handleCallback(CallbackRequest request) {
        return CompletableFuture.runAsync(() -> {
            // Обработка callback'ов (например, кнопки в VK)
            log.info("Processing callback: {}", request.callbackId());
        });
    }
    
    @Override
    public CompletableFuture<Void> handleAttachment(AttachmentInfo attachment) {
        return CompletableFuture.runAsync(() -> {
            // Обработка файлов и вложений
            log.info("Processing attachment: {}", attachment.fileName());
        });
    }
}
```

#### 5. Структура модулей после рефакторинга:

```
java-bot/
├── bot-core/
│   ├── api/
│   │   ├── adapter/
│   │   │   ├── IBotPlatformAdapter.java      ✅ НОВОЕ
│   │   │   ├── IInboundMessageHandler.java   ✅ НОВОЕ
│   │   │   ├── IOutboundMessenger.java       ✅ НОВОЕ
│   │   │   └── model/
│   │   │       ├── InboundMessage.java
│   │   │       ├── UserProfile.java
│   │   │       ├── MediaAttachment.java
│   │   │       └── ...
│   │   └── event/
│   ├── service/
│   │   ├── InboundMessageHandlerImpl.java    ✅ НОВОЕ
│   │   ├── ITicketService.java
│   │   ├── TicketService.java
│   │   ├── IFeedbackService.java
│   │   └── ...
│   └── repository/
│
├── bot-telegram/
│   ├── adapter/
│   │   ├── TelegramBotAdapter.java          ✅ НОВОЕ (impl IBotPlatformAdapter)
│   │   └── TelegramWebhookHandler.java      ✅ НОВОЕ (impl IInboundMessageHandler)
│   ├── config/
│   │   └── TelegramBotConfig.java
│   └── controller/
│       └── TelegramWebhookController.java
│
├── bot-vk/
│   ├── adapter/
│   │   ├── VkBotAdapter.java                ✅ НОВОЕ (impl IBotPlatformAdapter)
│   │   └── VkCallbackHandler.java           ✅ НОВОЕ (impl IInboundMessageHandler)
│   ├── config/
│   │   └── VkBotConfig.java
│   └── controller/
│       └── VkWebhookController.java
│
└── bot-max/
    ├── adapter/
    │   ├── MaxBotAdapter.java               ✅ НОВОЕ (impl IBotPlatformAdapter)
    │   └── MaxWebhookHandler.java           ✅ НОВОЕ (impl IInboundMessageHandler)
    ├── config/
    │   └── MaxBotConfig.java
    └── controller/
        └── MaxWebhookController.java
```

---

## Правило 2: Обязательное использование JPA Repository

### ❌ Неправильно (raw JDBC):

```java
@Service
public class DialogService {
    private final JdbcTemplate jdbcTemplate;
    
    public DialogSummary loadSummary() {
        long total = Objects.requireNonNullElse(
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets", Long.class), 
            0L
        );
        
        long open = Objects.requireNonNullElse(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE status = 'OPEN'", 
                Long.class
            ), 
            0L
        );
        
        return new DialogSummary(total, open, 0, List.of());
    }
}
```

### ✅ Правильно (JPA Repository):

```java
// Repository интерфейс
public interface IDialogRepository extends JpaRepository<Dialog, String> {
    
    /**
     * Find dialogs by operator ID
     */
    List<Dialog> findByOperatorId(String operatorId);
    
    /**
     * Find open dialogs
     */
    List<Dialog> findByStatus(String status);
    
    /**
     * Find dialogs created after date
     */
    List<Dialog> findByCreatedAtAfter(LocalDateTime date);
    
    /**
     * Custom JPQL query for complex logic
     */
    @Query("""
        SELECT d FROM Dialog d 
        WHERE d.status = 'OPEN' 
        AND d.createdAt < :cutoffDate
        ORDER BY d.createdAt DESC
    """)
    Page<Dialog> findOverdueDialogs(LocalDateTime cutoffDate, Pageable pageable);
    
    /**
     * Count by status
     */
    long countByStatus(String status);
}

// Service использует Repository
@Service
public class DialogService implements IDialogService {
    private final IDialogRepository dialogRepository;
    
    @Override
    public DialogSummary loadSummary() {
        long total = dialogRepository.count();
        long open = dialogRepository.countByStatus("OPEN");
        long inProgress = dialogRepository.countByStatus("IN_PROGRESS");
        
        return new DialogSummary(total, open, inProgress, List.of());
    }
    
    @Override
    public List<Dialog> loadDialogs(String operatorId) {
        return dialogRepository.findByOperatorId(operatorId);  // ← Type-safe
    }
    
    @Override
    public Page<Dialog> loadOverdueDialogs(LocalDateTime cutoff, Pageable page) {
        return dialogRepository.findOverdueDialogs(cutoff, page);
    }
}
```

### Преимущества JPA Repository:

| Аспект | JDBC | JPA |
|--------|------|-----|
| Type-safety | ❌ | ✅ |
| SQL Injection | ⚠️ Требует effort | ✅ Built-in |
| Lazy Loading | ❌ | ✅ |
| Caching | ❌ | ✅ (Hibernate) |
| Testability | ❌ | ✅ |
| Boilerplate | Много | Мало |

---

## Правило 3: Стратегия работы с несколькими БД

### Проблема:

Проект использует несколько SQLite файлов:
- `tickets.db` (заявки и сообщения)
- `users.db` (пользователи)
- `clients.db` (клиенты)
- `bot-<channelId>.db` (отдельные БД для каждого бота)

### Решение: DataSource конфигурация

```java
// spring-panel/src/main/java/com/example/panel/config/TicketsDataSourceConfig.java
@Configuration
public class TicketsDataSourceConfig {
    
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.tickets")
    public DataSourceProperties ticketsDataSourceProperties() {
        return new DataSourceProperties();
    }
    
    @Bean
    public DataSource ticketsDataSource() {
        return ticketsDataSourceProperties()
            .initializeDataSourceBuilder()
            .build();
    }
    
    @Bean
    public LocalContainerEntityManagerFactoryBean ticketsEntityManagerFactory(
        EntityManagerFactoryBuilder builder
    ) {
        return builder
            .dataSource(ticketsDataSource())
            .packages(com.example.panel.entity.class)
            .persistenceUnit("tickets")
            .build();
    }
    
    @Bean
    public PlatformTransactionManager ticketsTransactionManager(
        EntityManagerFactory ticketsEntityManagerFactory
    ) {
        return new JpaTransactionManager(ticketsEntityManagerFactory);
    }
}

// application.yml
spring:
  datasource:
    tickets:
      url: jdbc:sqlite:${APP_DB_TICKETS:tickets.db}
      driver-class-name: org.sqlite.JDBC
    users:
      url: jdbc:sqlite:${APP_DB_USERS:users.db}
      driver-class-name: org.sqlite.JDBC
    clients:
      url: jdbc:sqlite:${APP_DB_CLIENTS:clients.db}
      driver-class-name: org.sqlite.JDBC
```

---

## Правило 4: Кэширование данных

### ✅ Правильное использование @Cacheable:

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCacheNames(
            "dialogSummary",      // 5 минут
            "dialogDetails",      // 10 минут
            "userProfiles",       // 30 минут
            "dialogs"             // 1 минута
        );
        
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5)));
        
        return manager;
    }
}

// Service
@Service
public class DialogService implements IDialogService {
    
    @Cacheable(value = "dialogDetails", key = "#id")
    public DialogDetails findById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Dialog", id));
    }
    
    @Cacheable(value = "dialogSummary", key = "'all'")
    public DialogSummary loadSummary() {
        // Кэшируется на 5 минут
        return computeSummary();
    }
    
    @CacheEvict(value = "dialogDetails", key = "#dialogId")
    public void updateDialog(String dialogId, UpdateDialogRequest request) {
        repository.update(dialogId, request);
    }
    
    @CacheEvict(value = {"dialogSummary", "dialogs"}, allEntries = true)
    public void createDialog(CreateDialogRequest request) {
        repository.create(request);
    }
}
```

---

## Проверочный лист для review

- [ ] Bot модули используют IBotPlatformAdapter?
- [ ] Есть IInboundMessageHandler для обработки сообщений?
- [ ] Используется JPA Repository вместо raw JDBC?
- [ ] На методах сервисов используются @Query аннотации?
- [ ] Есть кэширование часто читаемых данных (@Cacheable)?
- [ ] При обновлении данных инвалидируется кэш (@CacheEvict)?
- [ ] Нет прямого наследования от SDK классов (TelegramLongPollingBot)?

---

**Статус:** Активный, вступает в силу сразу  
**Автор:** GitHub Copilot  
**Дата создания:** 8 апреля 2026  
