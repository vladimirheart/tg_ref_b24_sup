# Архитектурный аудит проекта Iguana CRM
**Дата:** 8 апреля 2026  
**Статус:** Критические проблемы выявлены  
**Актуализация:** 9 апреля 2026 (см. `docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md`)

---

## 📋 Выполнено

✅ Проверена структура spring-panel (28 контроллеров, 29 сервисов)  
✅ Проверена структура java-bot multi-module (4 модуля)  
✅ Проанализированы зависимости и слои  
✅ Выявлены нарушения SOLID принципов  

---

## 🔴 КРИТИЧЕСКИЕ ПРОБЛЕМЫ (P0)

### 1. Нарушение принципа разделения ответственности между ботами

**Локация:** 
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`

**Суть:** Каждый bot-модуль использует core сервисы напрямую, создавая плотную связанность. Нет абстракции для работы с платформ-специфичными деталями.

**Последствия:**
- Невозможно менять реализацию сервисов для разных платформ
- Сложно тестировать платформы изолированно
- Изменения в core влияют на все боты сразу

**Решение:** Создать абстракцию `BotAdapter` с интерфейсом `IBotPlatformAdapter` для изоляции платформ.

---

### 2. Отсутствие явного слоя DTO между Entity и API

**Локация:**
- `spring-panel/src/main/java/com/example/panel/entity/Ticket.java`
- `spring-panel/src/main/java/com/example/panel/controller/TaskApiController.java` (68-90)
- `java-bot/bot-core/src/main/java/com/example/supportbot/entity/Ticket.java`

**Суть:** Entity классы используются напрямую в контроллерах. API контракт не имеет защиты.

**Последствия:**
- Нет типизированной валидации API контракта
- Трудно отслеживать изменения API
- Нет поддержки версионирования API

**Решение:** Ввести слой DTO с маппингом через MapStruct или ручной маппер.

---

### 3. Частичная централизованная обработка ошибок

**Локация:**
- `spring-panel/src/main/java/com/example/panel/service/DialogService.java`
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/TelegramBotApplication.java`

**Суть:** Базовый обработчик исключений уже есть (`RestExceptionHandler`), но покрытие ограничено и нет единого формата для всех REST-ошибок.

**Решение:** Создать `@RestControllerAdvice` для централизованной обработки ошибок.

---

## 🟠 ВЫСОКИЙ ПРИОРИТЕТ (P1)

### 4. Монолитные сервисы со слишком большой ответственностью

**Примеры:**
- `DialogService` (~6600+ строк) — нарушение SRP
- `DialogApiController` (~5000+ строк) — смешивание бизнес-логики и маршрутизации
- до 11 инъекций зависимостей в одном контроллере

**Метрики проблемы:**
- Средний размер сервиса: 500 строк (цель: 200)
- DialogService содержит 100+ констант и 30+ методов

**Решение:** Разбить с применением CQRS:
```
DialogService ──→ DialogQueryService (чтение)
             ──→ DialogCommandService (запись)
             ──→ DialogMapper (преобразование)
```

---

### 5. Дублирование кода между модулями

**Примеры:**
- `SharedConfigService` реализован отдельно в:
  - `java-bot/bot-core/src/.../service/SharedConfigService.java`
  - `spring-panel/src/.../service/SharedConfigService.java`

**Решение:** Создать общий модуль `config-shared` с единственной реализацией.

---

### 6. Отсутствие интерфейсов для сервисов

**Проблема:** Все сервисы — конкретные классы, нет интерфейсов.

```java
@Service
public class DialogService {  // ❌ Конкретный класс
    // Нарушение Dependency Inversion Principle
}
```

**Решение:**
```java
public interface IDialogService { ... }

@Service
public class DialogService implements IDialogService { ... }
```

---

### 7. Нарушение Layered Architecture в bot-модулях

**Проблема:** Bot классы смешивают роли:
- Компонент (Component)
- Контроллер (обработка webhook'ов)
- Сервис (бизнес-логика)

```java
@Component
public class SupportBot extends TelegramLongPollingBot {
    // Наследование от SDK создает tight coupling
    private final TicketService ticketService;
    // Инъекции сервисов
    
    public void onUpdateReceived(Update update) {
        // Обработка логики здесь
    }
}
```

**Решение:** Разделить роли через Bot Adapter Pattern.

---

### 8. Неполное использование Spring Data JPA

**Проблема:** Используется raw JDBC вместо Repository.

```java
// ❌ DialogService.java
private final JdbcTemplate jdbcTemplate;

public DialogSummary loadSummary() {
    long total = Objects.requireNonNullElse(
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tickets", Long.class), 
        0L
    );
}
```

**Решение:** Переписать на JPA Repository для типизации и testability.

---

## 🟡 СРЕДНИЙ ПРИОРИТЕТ (P2)

### 9. Непоследовательное именование DTO/Model

Разные модули используют разные соглашения:
- `panel` использует `model/`
- `bot-core` использует `settings/dto/`

Различия между Entity и DTO реализованы непоследовательно (DTO-слой в проекте есть, но часть API использует entity напрямую).

---

### 10. Частично неформализованные Spring-конфигурации

`@EnableScheduling` и `@EnableCaching` уже включены в `PanelApplication`, но часть инфраструктурных практик (единый error contract, единые API-конвенции) пока не стандартизирована.

---

### 11. Отсутствие API versioning

Нет версионирования API при `/api/tasks` маршруте.

---

### 12. Ограниченное использование кэширования

Caffeine и `@Cacheable` уже используются (например в `AnalyticsService`), но кэширование применяется точечно и требует расширения на горячие запросы.

---

### 13. Отсутствие валидации в Entity

Entity содержат только getters/setters без логики и аннотаций валидации.

```java
@Entity
public class Channel {
    @Column(nullable = false, unique = true)
    private String token;  // ❌ Нет @NotBlank
    
    private Integer maxQuestions;  // ❌ Нет @Positive
}
```

---

## 📊 Таблица соответствия правилам

| Правило | Статус | Комментарий |
|---------|--------|-----------|
| Layered Architecture | ⚠️ Частично | Слои есть, но нарушения в bot-модулях |
| Dependency Inversion | ❌ НЕТ | Нет интерфейсов для сервисов |
| Single Responsibility | ❌ НЕТ | DialogService слишком большой |
| Don't Repeat Yourself | ❌ НЕТ | SharedConfigService дублируется |
| SOLID Principles | ⚠️ Частично | Только Open/Closed соблюдается |
| Spring Best Practices | ⚠️ Частично | Нужен полноценный `@RestControllerAdvice` и единый формат ошибок |

---

## 📈 Метрики качества

| Метрика | Текущее | Цель |
|---------|---------|------|
| Средний размер сервиса | 500 строк | 200 строк |
| Дублирующийся код | ~15% | < 5% |
| Unit-tests | есть (18 test classes в `spring-panel`) | 70%+ |
| Code Coverage | нет | 60%+ |
| Инъекции на сервис | до 11 в контроллерах | 3-5 |
| Использование интерфейсов | 0% | 100% |

---

## 🎯 Рекомендуемый план действий

### Фаза 1: Фундамент (Sprint 1-2)
- [ ] Создать интерфейсы для всех сервисов
- [ ] Добавить GlobalExceptionHandler
- [ ] Разработать Bot Adapter Pattern

### Фаза 2: Рефакторинг (Sprint 3-5)
- [ ] Разбить DialogService на Query и Command сервисы
- [ ] Ввести DTO слой с маппингом
- [ ] Объединить SharedConfigService в общий модуль

### Фаза 3: Интеграция (Sprint 6+)
- [ ] Внедрить CQRS для DialogService
- [ ] Добавить Event Bus (Kafka/RabbitMQ)
- [ ] Объединить API-версионирование

---

## 📁 Следующие шаги

1. Обсудить приоритеты с командой разработки
2. Создать architecture rules в `ai-context/rules/backend/`
3. Запланировать рефакторинг по спринтам
4. Добавить checkstyle правила в `pom.xml`

**Автор аудита:** GitHub Copilot  
**Статус:** Требуется деятельность  
