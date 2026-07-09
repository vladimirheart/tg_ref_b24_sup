# OOP Architecture Overview for Iguana

## Назначение документа

Этот документ описывает проект `Iguana` с точки зрения объектно-ориентированного
подхода. Цель документа:

- показать, как система разложена на объекты и ответственности;
- объяснить, где в проекте применяются инкапсуляция, абстракция,
  наследование и полиморфизм;
- зафиксировать текущую OOP-модель для дальнейшей разработки и рефакторинга.

Документ основан на актуальной структуре репозитория на `8 июля 2026`.

## 1. Общая картина проекта

`Iguana` представляет собой Java/Spring-монорепозиторий из двух основных
подсистем:

- `spring-panel/` - веб-панель операторов, настройки, аналитика, база знаний,
  мониторинг и API;
- `java-bot/` - набор Java-ботов и общее ядро обработки сообщений.

Внутри `java-bot/` проект разделен на модули:

- `bot-core` - общее доменное и сервисное ядро;
- `bot-telegram` - адаптер Telegram;
- `bot-vk` - адаптер VK;
- `bot-max` - адаптер MAX.

С точки зрения ООП это не набор "скриптов", а система взаимодействующих
объектов, где каждый слой имеет собственную зону ответственности.

## 2. OOP-модель проекта

Основная объектная модель проекта построена вокруг пяти типов компонентов:

1. `Entity`-объекты
   - Хранят состояние предметной области.
   - Примеры: `Ticket`, `Message`, `Channel`, `PanelUser`,
     `KnowledgeArticle`, `Notification`.

2. `Repository`-абстракции
   - Отвечают за доступ к данным и скрывают детали JPA/SQLite.
   - Примеры: `TicketRepository`, `MessageRepository`,
     `KnowledgeArticleRepository`.

3. `Service`-объекты
   - Инкапсулируют бизнес-логику и оркестрацию сценариев.
   - Примеры: `DialogReplyService`, `DialogListReadService`,
     `KnowledgeBaseService`, `MessagingService`.

4. `Controller`-объекты
   - Принимают HTTP-запросы и делегируют работу сервисам.
   - Примеры: `DialogListController`, `SettingsApiController`,
     `KnowledgeBaseController`.

5. `Model`/`DTO`/`record`-объекты
   - Передают данные между слоями и наружу.
   - Примеры: `DialogListItem`, `DialogSummary`, `NotificationDto`,
     `ApiErrorResponse`.

Такое разделение соответствует классической layered architecture, но внутри
каждого слоя используются именно объектные роли, а не процедурный код.

## 3. Как в проекте реализованы принципы ООП

### 3.1. Инкапсуляция

Инкапсуляция выражена через классы, которые скрывают детали своей работы и
предоставляют наружу только понятный контракт.

Примеры:

- `DialogReplyService` скрывает детали определения получателя, выбора канала,
  отправки сообщения и логирования результата;
- `MessagingService` скрывает логику выбора подходящего транспорта по
  платформе;
- `Ticket` инкапсулирует состояние обращения, а доступ к его ключевым полям
  идет через методы `getUserId()` и `getTicketId()` поверх составного ключа.

Итог: внешний код работает с понятными методами, а не знает внутренние детали
JPA, SQL, API канала или структуры базы.

### 3.2. Абстракция

Абстракция используется для отделения контракта от реализации.

Главные примеры:

- `OutboundMessenger` в `bot-core` задает общий интерфейс отправки сообщений;
- `AbstractSqliteDataSourceProperties` выделяет общую логику для SQLite
  datasource и позволяет создавать конкретные варианты через наследников;
- `JpaRepository`-интерфейсы формируют абстракцию доступа к данным.

Итог: верхний уровень работает не с конкретной платформой или конкретным JDBC
кодом, а с обобщенным контрактом.

### 3.3. Наследование

Наследование используется умеренно и в основном там, где оно действительно
оправдано инфраструктурно.

Ключевой пример:

- `SqliteDataSourceProperties` наследует
  `AbstractSqliteDataSourceProperties` и переиспользует общую логику
  нормализации пути, сборки JDBC URL и создания SQLite-файла.

Это хороший признак: проект не перегружен "иерархиями ради иерархий", а
предпочитает композицию там, где это безопаснее и проще.

### 3.4. Полиморфизм

Полиморфизм выражен наиболее наглядно в транспортном контуре ботов.

Интерфейс:

- `OutboundMessenger`

Реализации:

- `TelegramOutboundMessenger`
- `VkOutboundMessenger`
- `MaxOutboundMessenger`

`MessagingService` получает список реализаций через Spring DI, строит карту
`platform -> messenger` и дальше работает с ними одинаково, не зная деталей
конкретного канала.

Это один из самых чистых OOP-фрагментов проекта: единый контракт, несколько
платформенных реализаций, выбор поведения во время выполнения.

## 4. Слои и объектные роли

### 4.1. Domain layer

Пакет `entity` хранит объекты предметной области:

- `Ticket` - обращение клиента;
- `Message` / `ChatHistory` - история коммуникации;
- `Channel` - описание канала связи;
- `PanelUser`, `Role` - оператор и роли доступа;
- `KnowledgeArticle` - статья базы знаний;
- `Notification` - уведомление.

Отдельно выделены value-like объекты:

- `TicketId` - составной идентификатор обращения;
- `TaskLinkId` - составной идентификатор связи задач;
- многочисленные `record`-типы для lightweight-передачи данных.

### 4.2. Persistence layer

Пакет `repository` задает объектный доступ к хранилищу:

- `TicketRepository extends JpaRepository<Ticket, TicketId>`;
- `TaskRepository extends JpaRepository<Task, Long>`;
- `KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long>`.

Repository-интерфейсы позволяют не размазывать SQL по контроллерам и
бизнес-логике. Это усиливает SRP: сервис отвечает за сценарий, репозиторий -
за загрузку и сохранение данных.

### 4.3. Application/Service layer

Это самый насыщенный слой проекта. Здесь сосредоточена прикладная логика.

Типовые роли сервисов:

- orchestration services:
  `DialogService`, `DialogWorkspaceService`, `SettingsUpdateService`;
- domain services:
  `DialogTicketLifecycleService`, `DialogResponsibilityService`,
  `PermissionService`;
- integration services:
  `KnowledgeBaseNotionService`, `Bitrix24RestService`,
  `IikoApiMonitoringService`;
- transport helpers:
  `DialogReplyTransportService`, `ChannelTransportService`;
- runtime/config services:
  `SharedConfigService`, `BotRuntimeContractService`,
  `DialogSlaRuntimeService`.

Показательный пример - `DialogService`. Сейчас он играет роль thin facade:
делегирует работу специализированным сервисам чтения, аудита, телеметрии и
lifecycle-операций. Это соответствует OOP-подходу "объект делает одну вещь и
делает ее хорошо".

### 4.4. Presentation layer

Контроллеры в `spring-panel` в основном тонкие:

- получают HTTP-запрос;
- извлекают контекст пользователя;
- передают управление сервису;
- возвращают DTO или payload.

Пример:

- `DialogListController` не строит список диалогов сам, а делегирует это
  `DialogListReadService`.

Это правильное разделение ответственности между transport и business logic.

## 5. Композиция как основной стиль проектирования

Хотя в проекте есть наследование, базовый стиль архитектуры - композиция.

Примеры:

- `DialogListReadService` собирается из `DialogLookupReadService`,
  `SharedConfigService` и `DialogSlaRuntimeService`;
- `DialogReplyService` использует `DialogReplyTargetService`,
  `DialogReplyTransportService` и `DialogResponsibilityService`;
- `DialogService` агрегирует несколько специализированных сервисов вместо
  того, чтобы содержать всю логику внутри себя.

Преимущества такого подхода:

- проще тестировать;
- проще заменять реализацию;
- меньше риск появления giant service;
- легче развивать bounded context по частям.

## 6. Пример OOP-сценария в системе

Сценарий: оператор открывает список диалогов.

1. `DialogListController` принимает запрос `GET /api/dialogs`.
2. Контроллер передает имя оператора в `DialogListReadService`.
3. `DialogListReadService` запрашивает:
   - `DialogLookupReadService` для списка и summary;
   - `DialogSlaRuntimeService` для SLA-расчетов;
   - `SharedConfigService` для runtime-настроек.
4. Результат собирается в объектный payload и возвращается клиенту.

Сценарий: бот отправляет сообщение в конкретный канал.

1. `MessagingService` получает `Channel`.
2. По `channel.platform` выбирается реализация `OutboundMessenger`.
3. Вызывается нужный объект:
   - `TelegramOutboundMessenger`, или
   - `VkOutboundMessenger`, или
   - `MaxOutboundMessenger`.
4. Канальная реализация сама знает, как отправить сообщение в свою платформу.

Это хороший пример позднего выбора поведения через полиморфизм.

## 7. Оценка проекта с точки зрения SOLID

### S - Single Responsibility Principle

Состояние проекта в целом хорошее, особенно после разбиения крупных сервисов.
По коду видно движение к маленьким специализированным объектам:

- `DialogLookupReadService`
- `DialogConversationReadService`
- `DialogTicketLifecycleService`
- `DialogAuditService`

Риск остается в больших интеграционных сервисах и части runtime-сервисов, где
в одном классе еще может накапливаться много ответственности.

### O - Open/Closed Principle

Лучше всего принцип соблюден в модульных адаптерах каналов:

- можно добавить новую платформу, реализовав `OutboundMessenger`;
- можно добавить новый datasource properties-класс через наследование от
  `AbstractSqliteDataSourceProperties`.

### L - Liskov Substitution Principle

Реализации `OutboundMessenger` взаимозаменяемы в пределах общего контракта.
Это положительный признак корректного применения интерфейса.

### I - Interface Segregation Principle

В проекте часто используются компактные интерфейсы и специализированные
репозитории. Это хорошо. Но часть сервисов пока опирается не на интерфейсы, а
на concrete classes, что допустимо для Spring-приложения, но уменьшает
гибкость при замене реализации.

### D - Dependency Inversion Principle

Зависимости в целом внедряются через конструкторы, что соответствует DIP и
облегчает тестирование. Лучшие примеры:

- `MessagingService(List<OutboundMessenger> messengers)`
- `DialogListController(DialogListReadService dialogListReadService)`
- `DialogReplyService(...)`

## 8. Сильные стороны текущей OOP-архитектуры

- Четкое разделение на `controller -> service -> repository -> entity`.
- Хорошее использование Spring DI и constructor injection.
- Удачный полиморфизм в канальных адаптерах.
- Активное использование композиции вместо сложных иерархий.
- Выделение `record`-DTO для компактной и безопасной передачи данных.
- Постепенное превращение крупных сервисов в фасады и координаторы.

## 9. Зоны для улучшения

С точки зрения ООП проект уже зрелый, но есть несколько направлений роста:

- сократить использование `Map<String, Object>` в пользу более явных DTO и
  value objects;
- унифицировать shared domain/service контракты между `spring-panel` и
  `java-bot`, где сейчас есть частичное дублирование;
- продолжать дробить крупные integration/runtime-сервисы на smaller bounded
  services;
- выделять интерфейсы не только для адаптеров, но и для критичных внешних
  интеграций, где нужна замена реализации или изоляция в тестах.

## 10. Вывод

Проект `Iguana` реализован в объектно-ориентированном стиле и опирается на
зрелую многослойную архитектуру. Главные OOP-идеи в нем выражены так:

- состояние хранится в `entity`-объектах;
- поведение сосредоточено в `service`-объектах;
- доступ к данным инкапсулирован в `repository`;
- внешние каналы расширяются через интерфейсы и полиморфные реализации;
- развитие системы идет через композицию и декомпозицию крупных классов.

С практической точки зрения это означает, что проект хорошо подходит для
дальнейшего масштабирования: в него можно добавлять новые каналы, новые
сценарии операторской панели и новые интеграции без полного переписывания
существующей архитектуры.
