# Java CRM Iguana: Правила архитектуры

## Общие принципы

Проект использует **Layered Architecture (слоистую архитектуру)** с явным разделением на:

1. **Controller Layer** — HTTP обработка и маршрутизация
2. **Service Layer** — бизнес-логика
3. **Repository/DAO Layer** — доступ к данным
4. **Entity/Domain Layer** — объекты доменной модели

Все слои должны быть четко разделены и независимы.

---

## Правило 1: Обязательное использование интерфейсов для сервисов

### ✅ Правильно:

```java
// Service interface
public interface IDialogService {
    DialogSummary loadSummary();
    List<DialogListItem> loadDialogs(String operatorId);
}

// Service implementation
@Service
public class DialogService implements IDialogService {
    // реализация
}

// Injection
@RestController
public class DialogApiController {
    private final IDialogService dialogService;  // ← Инъекция по интерфейсу
}
```

### ❌ Неправильно:

```java
@Service
public class DialogService {  // ← Конкретный класс, не интерфейс
}

@RestController
public class DialogApiController {
    private final DialogService dialogService;  // ← Инъекция по конкретному классу
}
```

### Причина:
- Следование Dependency Inversion Principle (DIP)
- Облегчение тестирования (мокирование)
- Возможность замены реализации без изменения кода клиентов

---

## Правило 2: Обязательное использование DTO (Data Transfer Objects)

### ✅ Правильно:

```java
// Entity (только для БД)
@Entity
public class Ticket {
    @Id private String id;
    @Column private String text;
    @Column private LocalDateTime createdAt;
}

// Request DTO (для входящих данных)
public record CreateTicketRequest(
    @NotBlank String text,
    @NotBlank String clientId
) {}

// Response DTO (для исходящих данных)
public record TicketResponse(
    String id,
    String text,
    LocalDateTime createdAt
) {}

// Mapper
@Component
public class TicketMapper {
    public TicketResponse toResponse(Ticket entity) {
        return new TicketResponse(
            entity.getId(),
            entity.getText(),
            entity.getCreatedAt()
        );
    }
}

// Controller
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketApiController {
    private final ITicketService ticketService;
    private final TicketMapper mapper;
    
    @PostMapping
    public ResponseEntity<TicketResponse> create(@RequestBody CreateTicketRequest req) {
        Ticket ticket = ticketService.create(req.text(), req.clientId());
        return ResponseEntity.ok(mapper.toResponse(ticket));
    }
}
```

### ❌ Неправильно:

```java
@Entity
public class Ticket { ... }

@RestController
public class TicketApiController {
    @PostMapping
    public Ticket create(@RequestBody Ticket ticket) {  // ← Entity в API контракте
        return ticketService.create(ticket);
    }
}
```

### Причина:
- Разделение ответственности (Entity для БД, DTO для API)
- Независимость API контракта от внутренней структуры данных
- Валидация на уровне API контракта
- Поддержка versioning API

---

## Правило 3: Обязательная обработка исключений через GlobalExceptionHandler

### ✅ Правильно:

```java
// Custom Exception
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

public class BusinessLogicException extends RuntimeException {
    public BusinessLogicException(String message) {
        super(message);
    }
}

// Global Exception Handler
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }
    
    @ExceptionHandler(BusinessLogicException.class)
    public ResponseEntity<ErrorResponse> handleBusinessLogic(BusinessLogicException ex) {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BUSINESS_ERROR", ex.getMessage()));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new ErrorResponse("INTERNAL_ERROR", "Something went wrong"));
    }
}

// Response DTO
public record ErrorResponse(
    String code,
    String message
) {}

// Service
@Service
public class DialogService implements IDialogService {
    public DialogDetails getById(String id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Dialog not found: " + id));
    }
}
```

### ❌ Неправильно:

```java
@RestController
public class DialogApiController {
    @GetMapping("/{id}")
    public DialogDetails get(@PathVariable String id) {
        try {
            return service.getById(id);
        } catch (DataAccessException ex) {
            return null;  // ← Молчаливое игнорирование ошибки
        } catch (Exception ex) {
            log.warn("Something wrong...", ex);  // ← Разбросанная обработка
            throw new RuntimeException("Internal error");
        }
    }
}
```

### Причина:
- Централизованная и последовательная обработка ошибок
- Единый API контракт для ошибок
- Логирование и мониторинг в одном месте

---

## Правило 4: Максимальный размер сервиса — 300 строк

### Метрики:
- **Идеальный размер:** 100-200 строк
- **Приемлемый размер:** 200-300 строк
- **Неприемлемый размер:** > 300 строк — требуется рефакторинг

### Как рефакторить:

```java
// ❌ Монолитный сервис (500+ строк)
@Service
public class DialogService {
    private final repository;
    private final feedbackService;
    private final ticketService;
    private final analyticsService;
    private final mailService;
    
    // 50+ методов смешанной ответственности
    
    public List<DialogListItem> loadDialogs(...) { }
    public DialogSummary loadSummary() { }
    public void createDialog(...) { }
    public void updateDialog(...) { }
    public void replyDialog(...) { }
    public void closeDialog(...) { }
    public void sendNotification(...) { }
    public void generateAnalytics(...) { }
}

// ✅ Разделенные сервисы (100-200 строк каждый)
public interface IDialogQueryService {
    List<DialogListItem> loadDialogs(...);
    DialogDetails loadById(String id);
    DialogSummary loadSummary();
}

@Service
public class DialogQueryService implements IDialogQueryService {
    private final IDialogRepository repository;
    
    // 10-15 методов чтения
}

public interface IDialogCommandService {
    String create(CreateDialogRequest req);
    void update(String id, UpdateDialogRequest req);
    void reply(String id, ReplyRequest req);
    void close(String id);
}

@Service
public class DialogCommandService implements IDialogCommandService {
    private final IDialogRepository repository;
    private final IDialogEventPublisher eventPublisher;
    
    // 8-12 методов записи
}

public interface IDialogNotificationService {
    void notifyReplied(String dialogId);
    void notifyClosed(String dialogId);
}

@Service
public class DialogNotificationService implements IDialogNotificationService {
    private final IMailService mailService;
    private final ITelegramService telegramService;
    
    // 5-7 методов уведомления
}
```

### Причина:
- Single Responsibility Principle
- Легче тестировать
- Легче менять и расширять
- Меньше зависимостей

---

## Правило 5: Разделение ответственности в bot-модулях

### ✅ Правильная структура:

```
java-bot/
├── bot-core/
│   ├── api/
│   │   ├── adapter/
│   │   │   ├── IBotPlatformAdapter.java  (интерфейс)
│   │   │   ├── IInboundMessageHandler.java
│   │   │   └── IOutboundMessenger.java
│   │   └── event/
│   │       ├── MessageReceivedEvent.java
│   │       └── FeedbackSubmittedEvent.java
│   ├── service/
│   │   ├── ITicketService.java
│   │   ├── IFeedbackService.java
│   │   └── IChatHistoryService.java
│   └── repository/
│       ├── ITicketRepository.java
│       └── IChatMessageRepository.java
│
├── bot-telegram/
│   ├── config/
│   │   └── TelegramBotConfig.java
│   ├── adapter/
│   │   ├── TelegramBotAdapter.java (impl IBotPlatformAdapter)
│   │   └── TelegramWebhookHandler.java (impl IInboundMessageHandler)
│   └── controller/
│       └── TelegramWebhookController.java
│
├── bot-vk/
│   ├── config/
│   │   └── VkBotConfig.java
│   ├── adapter/
│   │   ├── VkBotAdapter.java (impl IBotPlatformAdapter)
│   │   └── VkWebhookHandler.java (impl IInboundMessageHandler)
│   └── controller/
│       └── VkWebhookController.java
│
└── bot-max/
    ├── config/
    │   └── MaxBotConfig.java
    ├── adapter/
    │   ├── MaxBotAdapter.java (impl IBotPlatformAdapter)
    │   └── MaxWebhookHandler.java (impl IInboundMessageHandler)
    └── controller/
        └── MaxWebhookController.java
```

### Интерфейсы:

```java
// bot-core/api/adapter/IBotPlatformAdapter.java
public interface IBotPlatformAdapter {
    /**
     * Отправить сообщение в платформу (например, Telegram)
     */
    CompletableFuture<Void> sendMessage(String chatId, String text);
    
    /**
     * Получить профиль пользователя из платформы
     */
    CompletableFuture<UserProfile> getUserProfile(String userId);
}

// bot-telegram/adapter/TelegramBotAdapter.java
@Component
public class TelegramBotAdapter implements IBotPlatformAdapter {
    private final TelegramClient client;
    
    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String text) {
        // Телеграм специфичная реализация
    }
}

// bot-vk/adapter/VkBotAdapter.java
@Component
public class VkBotAdapter implements IBotPlatformAdapter {
    private final VkClient client;
    
    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String text) {
        // VK специфичная реализация
    }
}
```

### Причина:
- Изоляция платформ-специфичного кода
- Возможность добавления новых платформ без изменения core
- Легче тестировать каждую платформу отдельно

---

## Правило 6: Использование Spring Data JPA Repository

### ✅ Правильно:

```java
public interface IDialogRepository extends JpaRepository<Dialog, String> {
    List<Dialog> findByOperatorId(String operatorId);
    
    List<Dialog> findByStatusAndCreatedAfter(DialogStatus status, LocalDateTime since);
    
    @Query("""
        SELECT d FROM Dialog d 
        WHERE d.status = 'OPEN' 
        AND d.createdAt < :cutoffDate
        ORDER BY d.createdAt DESC
    """)
    Page<Dialog> findOverdueDialogs(LocalDateTime cutoffDate, Pageable pageable);
}

// Service
@Service
public class DialogService {
    private final IDialogRepository repository;
    
    public List<Dialog> loadDialogs(String operatorId) {
        return repository.findByOperatorId(operatorId);  // ← Type-safe
    }
}
```

### ❌ Неправильно:

```java
@Service
public class DialogService {
    private final JdbcTemplate jdbcTemplate;
    
    public List<Dialog> loadDialogs(String operatorId) {
        return jdbcTemplate.query(  // ← Raw SQL string
            "SELECT * FROM dialog WHERE operator_id = ?",
            new Object[]{operatorId},
            (rs, rowNum) -> new Dialog(...)
        );
    }
}
```

### Причина:
- Type-safe запросы
- Встроенная оптимизация (lazy loading, кэширование)
- Проще тестировать
- Защита от SQL injection

---

## Правило 7: Обязательный API versioning

### ✅ Правильно:

```java
// Version 1 API
@RestController
@RequestMapping("/api/v1/dialogs")
public class DialogApiControllerV1 {
    @GetMapping
    public List<DialogResponse> list() { }
    
    @PostMapping
    public DialogResponse create(@RequestBody CreateDialogRequest req) { }
}

// Version 2 API (с улучшениями)
@RestController
@RequestMapping("/api/v2/dialogs")
public class DialogApiControllerV2 {
    @GetMapping
    public Page<DialogResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) { }  // ← Добавлена пагинация
    
    @PostMapping
    public ResponseEntity<DialogResponse> create(@RequestBody CreateDialogRequest req) { }
}
```

### Причина:
- Поддержка старых клиентов при изменении API
- Контролируемое устаревание старых версий

---

## Правило 8: Использование аннотаций валидации

### ✅ Правильно:

```java
public record CreateDialogRequest(
    @NotBlank(message = "Text is required")
    String text,
    
    @NotBlank(message = "Client ID is required")
    String clientId,
    
    @Email(message = "Invalid email format")
    String email,
    
    @Positive(message = "Priority must be positive")
    Integer priority
) {}

@Entity
public class Dialog {
    @Id
    @NotBlank
    private String id;
    
    @Column(nullable = false, length = 5000)
    @NotBlank
    @Size(min = 1, max = 5000)
    private String text;
    
    @Column(nullable = false)
    @Positive(message = "Priority must be positive")
    private Integer priority;
}
```

### Причина:
- Валидация на уровне контракта
- Единообразное обращение с ошибками валидации

---

## Правило 9: Разбирать BeanCreationException-ы при стартапе

Все bean зависимости должны быть явно разрешены при стартапе приложения.

### ✅ Правильно:

```java
@Configuration
public class BeanConfig {
    @Bean
    public IDialogService dialogService(
        IDialogRepository repository,
        IDialogEventPublisher eventPublisher
    ) {
        return new DialogService(repository, eventPublisher);
    }
}
```

### ❌ Неправильно:

Неявные зависимости, которые могут привести к `BeanCreationException` при стартапе.

---

## Правило 10: Логирование через SLF4J

### ✅ Правильно:

```java
@Service
public class DialogService implements IDialogService {
    private static final Logger log = LoggerFactory.getLogger(DialogService.class);
    
    public void create(CreateDialogRequest req) {
        log.info("Creating dialog for client: {}", req.clientId());
        
        try {
            // implementation
        } catch (Exception ex) {
            log.error("Failed to create dialog for client {}", req.clientId(), ex);
            throw new BusinessLogicException("Failed to create dialog", ex);
        }
    }
}
```

### ❌ Неправильно:

```java
System.out.println("Создание диалога...");  // ❌
ex.printStackTrace();  // ❌
```

---

## Соглашение об именовании

### Пакеты:

```
com.example.panel               // Главный модуль panel
├── api                         // Публичный API модуля
│   ├── adapter                 // Адаптеры (pattern Adapter)
│   ├── event                   // События (domain events)
│   └── dto                     // DTO классы для API контракта
├── config                      // Spring конфигурация
├── controller                  // HTTP контроллеры
├── converter                   // Конвертеры (Entity <=> DTO)
├── entity                      // JPA Entity для БД
├── exception                   // Пользовательские исключения
├── model                       // DTO модели (Request/Response)
├── repository                  // JPA Repository
└── service                     // Бизнес-логика сервисы
    ├── impl                    // Реализация интерфейсов
    └── event                   // Event обработчики

com.example.supportbot          // java-bot модуль
├── bot-core                    // Общая логика для всех ботов
│   ├── api
│   │   ├── adapter             // IBotPlatformAdapter, IMessageHandler
│   │   ├── event               // Domain events
│   │   └── spi                 // Service Provider Interface
│   ├── entity
│   ├── repository
│   └── service
├── bot-telegram               // Телеграм бот
│   ├── adapter                 // TelegramBotAdapter
│   └── controller              // WebhookController
├── bot-vk                     // VK бот
├── bot-max                    // MAX бот
```

### Классы:

```
Interface:      IDialogService, IBotPlatformAdapter
Implementation: DialogService, TelegramBotAdapter
DTO Request:    CreateDialogRequest, UpdateDialogRequest
DTO Response:   DialogResponse, DialogListItemResponse
Entity:         Dialog, Ticket, Channel
Repository:     IDialogRepository, ITicketRepository
Event:          DialogCreatedEvent, DialogClosedEvent
Exception:      ResourceNotFoundException, BusinessLogicException
Mapper:         DialogMapper, TicketMapper
```

---

## Проверочный лист для code review

- [ ] Сервис имеет интерфейс (`I` префикс)?
- [ ] На вводе используются Request DTO, на выводе Response DTO?
- [ ] Есть GlobalExceptionHandler для исключений?
- [ ] Размер сервиса меньше 300 строк?
- [ ] Используется JPA Repository вместо raw JDBC?
- [ ] API имеет версию (`/api/v1`, `/api/v2`)?
- [ ] Entity имеют аннотации валидации?
- [ ] Логирование через SLF4J + Logger?
- [ ] Нет дублирования кода с другими модулями?
- [ ] Bot модули используют Adapter Pattern?

---

**Статус:** Активный, вступает в силу сразу  
**Автор:** GitHub Copilot  
**Дата создания:** 8 апреля 2026  
**Последнее обновление:** 8 апреля 2026
