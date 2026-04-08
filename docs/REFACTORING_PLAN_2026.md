# План рефакторинга: Архитектура проекта Iguana

**Статус:** Требуется деятельность  
**Дата:** 8 апреля 2026  
**Приоритет:** ВЫСОКИЙ

---

## 📋 Краткий обзор

В результате архитектурного аудита выявлены 13 проблем, из которых **3 критические**, **5 высокого приоритета** и **5 среднего приоритета**.

Этот документ содержит пошаговый план для устранения этих проблем.

---

## 🎯 Цели рефакторинга

- [ ] **Улучшить code quality** (снизить дублирование, размер сервисов)
- [ ] **Повысить testability** (интерфейсы для всех сервисов)
- [ ] **Стандартизировать архитектуру** (применить SOLID принципы)
- [ ] **Облегчить расширение** (Bot Adapter Pattern для новых платформ)
- [ ] **Улучшить устойчивость** (глобальная обработка ошибок, логирование)

---

## 📊 Метрики успеха

| Метрика | Текущее | Цель |
|---------|---------|------|
| Средний размер сервиса | 500 строк | **200 строк** |
| Сервисы с интерфейсами | 0% | **100%** |
| Дублирующийся код | ~15% | **< 5%** |
| Unit-тесты | 0% test classes | **70%+ code coverage** |
| Use of raw JDBC | High | **0% (JPA only)** |
| API versioning | Отсутствует | **/api/v1, /api/v2** |

---

## 📅 План по фазам

### **ФАЗА 1: Фундамент (Sprint 1-2) — Immediate Actions**
*Время: 2-3 недели | Сложность: СРЕДНЯЯ | Риск: LOW*

#### ✅ 1.1 Создать интерфейсы для всех сервисов

**Что делать:**
- Создать интерфейсы `I<ServiceName>` для каждого сервиса
- Переименовать текущий класс сервиса (при необходимости)
- Все инъекции зависимостей переделать на интерфейсы

**Локация:**
```
src/main/java/com/example/panel/service/
├── IDialogService.java (новый интерфейс)
├── DialogService.java (реализация)
├── ITicketService.java (новый интерфейс)
├── TicketService.java (реализация)
└── ... (для всех сервисов)
```

**Пример:**
```java
// ДО (неправильно)
@Service
public class DialogService { }

@RestController
public class DialogApiController {
    private final DialogService service;  // ❌ Конкретный класс
}

// ПОСЛЕ (правильно)
public interface IDialogService { }

@Service
public class DialogService implements IDialogService { }

@RestController
public class DialogApiController {
    private final IDialogService service;  // ✅ Интерфейс
}
```

**Чек-лист:**
- [ ] Созданы интерфейсы для всех сервисов
- [ ] Все @Autowired имеют тип интерфейса
- [ ] Код компилируется без ошибок
- [ ] Все существующие тесты проходят

---

#### ✅ 1.2 Добавить GlobalExceptionHandler

**Что делать:**
- Создать класс `GlobalExceptionHandler` с аннотацией `@RestControllerAdvice`
- Создать пользовательские исключения (ResourceNotFoundException, BusinessLogicException, etc)
- Заменить все try-catch блоки на выброс исключений

**Локация:**
```
src/main/java/com/example/panel/
├── exception/
│   ├── PanelException.java (базовое)
│   ├── ResourceNotFoundException.java
│   ├── BusinessLogicException.java
│   ├── UnauthorizedException.java
│   └── GlobalExceptionHandler.java
└── model/response/
    └── ErrorResponse.java
```

**Пример структуры исключений:**
```java
public abstract class PanelException extends RuntimeException {
    private final String code;
    private final int httpStatus;
    // ...
}

public class ResourceNotFoundException extends PanelException {
    public ResourceNotFoundException(String resourceName, String id) {
        super("RESOURCE_NOT_FOUND", 
              resourceName + " not found: " + id, 
              404);
    }
}

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse(ex.getCode(), ex.getMessage()));
    }
}
```

**Чек-лист:**
- [ ] Создана иерархия исключений
- [ ] GlobalExceptionHandler обрабатывает все типы ошибок
- [ ] ErrorResponse DTO содержит code, message, timestamp
- [ ] Все контроллеры используют пользовательские исключения

---

#### ✅ 1.3 Создать базовую структуру Bot Adapter Pattern

**Что делать:**
- Создать интерфейсы `IBotPlatformAdapter` и `IInboundMessageHandler` в bot-core
- Создать классы модели (InboundMessage, UserProfile, etc)
- Подготовить структуру папок для адаптеров

**Локация:**
```
java-bot/bot-core/src/main/java/com/example/supportbot/
├── api/
│   ├── adapter/
│   │   ├── IBotPlatformAdapter.java
│   │   ├── IInboundMessageHandler.java
│   │   └── model/
│   │       ├── InboundMessage.java
│   │       ├── UserProfile.java
│   │       └── ...
│   └── event/
│       ├── MessageReceivedEvent.java
│       └── ...
```

**Чек-лист:**
- [ ] Созданы интерфейсы IBotPlatformAdapter и IInboundMessageHandler
- [ ] Созданы DTO классы для моделей
- [ ] bot-core компилируется без ошибок

---

### **ФАЗА 2: Рефакторинг основных сервисов (Sprint 3-5) — Core Services**
*Время: 4-6 недель | Сложность: ВЫСОКАЯ | Риск: MEDIUM*

#### ✅ 2.1 Разбить DialogService на Query и Command сервисы

**Что делать:**
- Выделить логику чтения в `DialogQueryService`
- Выделить логику записи в `DialogCommandService`
- Выделить маппирование в `DialogMapper`
- Выделить уведомления в `DialogNotificationService`

**Текущее состояние:**
```
DialogService (2000+ строк)
├── loadSummary() — Query
├── loadDialogs(...) — Query
├── findById(...) — Query
├── create(...) — Command
├── update(...) — Command
├── reply(...) — Command
├── close(...) — Command
├── sendNotification(...) — Notification
└── ... (еще 30+ методов)
```

**Целевое состояние:**
```
IDialogQueryService (200 строк)
├── loadSummary()
├── loadDialogs()
├── findById()
└── findByStatus()

IDialogCommandService (150 строк)
├── create()
├── update()
├── reply()
└── close()

IDialogNotificationService (100 строк)
├── notifyReplied()
├── notifyClosed()
└── notifyAssigned()

DialogMapper (100 строк)
├── toResponse()
├── toEntity()
└── toListItem()
```

**Пример разделения методов:**

Шаг 1: Определить интерфейсы
```java
public interface IDialogQueryService {
    DialogSummary loadSummary();
    List<DialogListItem> loadDialogs(String operatorId);
    DialogDetails findById(String id);
    Page<Dialog> findByStatus(String status, Pageable page);
}

public interface IDialogCommandService {
    String create(CreateDialogRequest request);
    void update(String id, UpdateDialogRequest request);
    void reply(String id, String replyText);
    void close(String id);
}
```

Шаг 2: Переместить методы
```java
@Service
public class DialogQueryService implements IDialogQueryService {
    private final IDialogRepository repository;
    
    @Cacheable(value = "dialogSummary")
    public DialogSummary loadSummary() {
        // Реализация
    }
    
    public List<DialogListItem> loadDialogs(String operatorId) {
        // Реализация
    }
}

@Service
public class DialogCommandService implements IDialogCommandService {
    private final IDialogRepository repository;
    private final IDialogEventPublisher eventPublisher;
    
    public String create(CreateDialogRequest request) {
        // Реализация
        eventPublisher.publish(new DialogCreatedEvent(...));
    }
}
```

Шаг 3: Обновить контроллер
```java
@RestController
@RequestMapping("/api/v1/dialogs")
public class DialogApiController {
    private final IDialogQueryService queryService;
    private final IDialogCommandService commandService;
    
    @GetMapping
    public List<DialogResponse> list() {
        return queryService.loadDialogs(...)
            .stream()
            .map(mapper::toResponse)
            .toList();
    }
    
    @PostMapping
    public ResponseEntity<DialogResponse> create(@RequestBody CreateDialogRequest req) {
        String id = commandService.create(req);
        return ResponseEntity.created(URI.create("/api/v1/dialogs/" + id))
            .body(mapper.toResponse(queryService.findById(id)));
    }
}
```

**Чек-лист:**
- [ ] DialogService разбит на 4 отдельных сервиса
- [ ] Каждый сервис имеет не более 200 строк
- [ ] Интерфейсы четко определены
- [ ] Все существующие функции переработаны
- [ ] Unit-тесты обновлены/добавлены

---

#### ✅ 2.2 Ввести DTO слой для API контрактов

**Что делать:**
- Создать Request DTO для всех POST/PUT эндпоинтов
- Создать Response DTO для всех GET/POST/PUT эндпоинтов
- Добавить Mapper классы для конвертации Entity ↔ DTO
- Добавить валидацию в Request DTO

**Локация:**
```
src/main/java/com/example/panel/model/
├── request/
│   ├── CreateDialogRequest.java
│   ├── UpdateDialogRequest.java
│   ├── CreateTaskRequest.java
│   └── ...
├── response/
│   ├── DialogResponse.java
│   ├── DialogListItemResponse.java
│   ├── TaskResponse.java
│   └── ...
└── mapper/
    ├── DialogMapper.java
    ├── TaskMapper.java
    └── ...
```

**Пример:**
```java
// Request DTO (с валидацией)
public record CreateDialogRequest(
    @NotBlank String text,
    @NotBlank String clientId,
    @Email String clientEmail,
    @Min(1) @Max(5) Integer priority
) {}

// Response DTO (для API контракта)
public record DialogResponse(
    String id,
    String text,
    String clientId,
    DialogStatus status,
    LocalDateTime createdAt
) {}

// Mapper (Entity -> DTO)
@Component
public class DialogMapper {
    public DialogResponse toResponse(Dialog entity) {
        return new DialogResponse(
            entity.getId(),
            entity.getText(),
            entity.getClientId(),
            DialogStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt()
        );
    }
}
```

**Чек-лист:**
- [ ] Созданы Request DTO для всех POST/PUT контроллеров
- [ ] Созданы Response DTO для всех API эндпоинтов
- [ ] Добавлена валидация Jakarta Validation
- [ ] Созданы Mapper классы для всех entity типов
- [ ] Контроллеры используют DTO вместо Entity

---

#### ✅ 2.3 Переписать DialogService на JPA Repository

**Что делать:**
- Заменить JdbcTemplate на JPA Repository
- Переписать SQL запросы на JPQL
- Добавить кастомные Query методы

**Локация:**
```
src/main/java/com/example/panel/repository/
├── IDialogRepository.java (новый JPA Repository)
├── ITicketRepository.java
├── ITaskRepository.java
└── ...
```

**Пример:**
```java
// Было (неправильно - raw JDBC)
@Service
public class DialogService {
    private final JdbcTemplate jdbcTemplate;
    
    public long countOpen() {
        return Objects.requireNonNullElse(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dialog WHERE status = 'OPEN'", 
                Long.class
            ), 0L
        );
    }
}

// Стало (правильно - JPA)
public interface IDialogRepository extends JpaRepository<Dialog, String> {
    long countByStatus(String status);
    List<Dialog> findByOperatorId(String operatorId);
    
    @Query("""
        SELECT d FROM Dialog d 
        WHERE d.status = 'OPEN' 
        ORDER BY d.createdAt DESC
    """)
    Page<Dialog> findOpenDialogs(Pageable page);
}

@Service
public class DialogService {
    private final IDialogRepository repository;
    
    public long countOpen() {
        return repository.countByStatus("OPEN");  // ✅ Type-safe
    }
}
```

**Чек-лист:**
- [ ] Все JdbcTemplate вызовы заменены на Repository методы
- [ ] Добавлены @Query аннотации для сложных запросов
- [ ] Нет raw SQL строк в сервисах
- [ ] Все findOne(), findAll(), save() используют Repository

---

#### ✅ 2.4 Объединить SharedConfigService

**Что делать:**
- Удалить дублирующиеся реализации SharedConfigService
- Создать отдельный модуль `config-shared-lib`
- Подключить этот модуль к spring-panel и bot-core

**Структура:**
```
config-shared-lib/
├── pom.xml
└── src/main/java/com/example/shared/config/
    ├── SharedConfigService.java
    ├── SharedConfigLoader.java
    └── model/
        ├── Settings.json
        ├── Locations.json
        └── OrgStructure.json
```

**Чек-лист:**
- [ ] Создан модуль config-shared-lib
- [ ] Удалены дублирующиеся реализации
- [ ] spring-panel зависит от config-shared-lib
- [ ] bot-core зависит от config-shared-lib
- [ ] Нет конфликтов версий

---

### **ФАЗА 3: Многоплатформность Bot (Sprint 6-8) — Bot Adapter Pattern**
*Время: 3-4 недели | Сложность: ОЧЕНЬ ВЫСОКАЯ | Риск: MEDIUM-HIGH*

#### ✅ 3.1 Реализовать TelegramBotAdapter

**Что делать:**
- Реализовать IBotPlatformAdapter для Telegram
- Реализовать IInboundMessageHandler для Telegram
- Переместить платформ-специфичный код из core

**Локация:**
```
java-bot/bot-telegram/
├── adapter/
│   ├── TelegramBotAdapter.java (impl IBotPlatformAdapter)
│   └── TelegramMessageHandler.java (impl IInboundMessageHandler)
├── controller/
│   └── TelegramWebhookController.java
└── config/
    └── TelegramBotConfig.java
```

**Пример:**
```java
@Component
public class TelegramBotAdapter extends TelegramLongPollingBot implements IBotPlatformAdapter {
    private final IInboundMessageHandler messageHandler;
    
    @Override
    public CompletableFuture<Void> sendMessage(String chatId, String text) {
        // Telegram специфичная реализация
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        InboundMessage message = convertToInboundMessage(update);
        messageHandler.handleInboundMessage(message);
    }
}

@Component
public class TelegramMessageHandler implements IInboundMessageHandler {
    private final ITicketService ticketService;  // ← core logic
    
    @Override
    public CompletableFuture<Void> handleInboundMessage(InboundMessage message) {
        // Platform-agnostic logic
        ticketService.processIncomingMessage(...);
    }
}
```

**Чек-лист:**
- [ ] TelegramBotAdapter реализует IBotPlatformAdapter
- [ ] TelegramMessageHandler реализует IInboundMessageHandler
- [ ] Все Telegram SDK логика в адаптере
- [ ] Bot-core не знает о Telegram
- [ ] Функциональность сохранена

---

#### ✅ 3.2 Реализовать VkBotAdapter

(Аналогично TelegramBotAdapter)

---

#### ✅ 3.3 Реализовать MaxBotAdapter

(Аналогично TelegramBotAdapter)

---

### **ФАЗА 4: Интеграция и Optimization (Sprint 9-10) — Integration Phase**
*Время: 2-3 недели | Сложность: СРЕДНЯЯ | Риск: LOW*

#### ✅ 4.1 API Versioning

**Что делать:**
- Переименовать существующие эндпоинты на `/api/v1`
- Создать новые эндпоинты `/api/v2` с улучшениями
- Добавить deprecated header к v1 API

**Пример:**
```java
// V1 (legacy)
@RestController
@RequestMapping("/api/v1/dialogs")
@Deprecated(since = "2026-04-08", forRemoval = false)
public class DialogApiControllerV1 { ... }

// V2 (new)
@RestController
@RequestMapping("/api/v2/dialogs")
public class DialogApiControllerV2 { ... }
```

---

#### ✅ 4.2 Включить @EnableCaching

**Что делать:**
- Добавить @EnableCaching в Application класс
- Добавить @Cacheable на часто читаемые методы
- Добавить @CacheEvict на методы записи

---

#### ✅ 4.3 Добавить OpenAPI/Swagger документацию

**Что делать:**
- Добавить springdoc-openapi зависимость
- Аннотировать контроллеры с @Tag, @Operation, @ApiResponse
- Сгенерировать swagger.yaml документацию

---

## ❌ Что НЕ надо делать

- ❌ Не переписывайте существующие функции дважды
- ❌ Не меняйте БД schema (используйте миграции)
- ❌ Не удаляйте старые API эндпоинты (оставьте с @Deprecated)
- ❌ Не добавляйте новые зависимости без обсуждения
- ❌ Не разбирайте адаптеры без соответствующей фазы

---

## 🔍 Контроль качества

### Unit-тесты для новых сервисов:

```java
@ExtendWith(MockitoExtension.class)
class DialogCommandServiceTest {
    
    @Mock
    private IDialogRepository repository;
    
    @InjectMocks
    private DialogCommandService service;
    
    @Test
    void shouldCreateDialog() {
        // Arrange
        CreateDialogRequest request = new CreateDialogRequest(
            "Hello", "client-1", "client@example.com", 1
        );
        Dialog expected = new Dialog();
        expected.setId("dialog-1");
        
        when(repository.save(any()))
            .thenReturn(expected);
        
        // Act
        String id = service.create(request);
        
        // Assert
        assertEquals("dialog-1", id);
        verify(repository).save(any());
    }
}
```

### Integration-тесты:

```java
@SpringBootTest
class DialogApiControllerTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldReturnDialogById() {
        ResponseEntity<DialogResponse> response = restTemplate.getForEntity(
            "/api/v1/dialogs/dialog-1",
            DialogResponse.class
        );
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}
```

---

## 📊 Tracking Progress

| Фаза | Задача | Статус | Спринт | Owner |
|------|--------|--------|--------|-------|
| 1 | Интерфейсы для сервисов | TODO | Sprint 1-2 | |
| 1 | GlobalExceptionHandler | TODO | Sprint 1-2 | |
| 1 | Bot Adapter Pattern (базовая) | TODO | Sprint 1-2 | |
| 2 | DialogService → Query/Command | TODO | Sprint 3-5 | |
| 2 | DTO слой | TODO | Sprint 3-5 | |
| 2 | JPA Repository (вместо JDBC) | TODO | Sprint 3-5 | |
| 2 | Объединить SharedConfigService | TODO | Sprint 3-5 | |
| 3 | TelegramBotAdapter | TODO | Sprint 6-8 | |
| 3 | VkBotAdapter | TODO | Sprint 6-8 | |
| 3 | MaxBotAdapter | TODO | Sprint 6-8 | |
| 4 | API Versioning | TODO | Sprint 9-10 | |
| 4 | Кэширование | TODO | Sprint 9-10 | |
| 4 | Swagger документация | TODO | Sprint 9-10 | |

---

## 📞 Контакты и Q&A

**Как обсудить приоритеты?**
Встреча с архитектором проекта для согласования спринтов.

**Какие есть риски?**
- Потенциальные боте регрессии во время рефакторинга (требуется обширное тестирование)
- Задержки в развертывании новых функций во время переписи

**Как минимизировать риски?**
- Каждую фазу работы начинать с полного coverage unit-тестами
- Deployment новых версий в staging перед production
-备份БД перед большими изменениями

---

**Статус:** Требуется утверждение  
**Автор:** GitHub Copilot  
**Дата создания:** 8 апреля 2026  
**Дата последнего обновления:** 8 апреля 2026
