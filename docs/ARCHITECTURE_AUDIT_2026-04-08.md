# Архитектурный аудит проекта Iguana CRM
**Дата:** 8 апреля 2026  
**Статус:** Актуально, но в активной фазе исправления  
**Актуализация:** 9 апреля 2026 (см. `docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md`)  
**Последняя актуализация:** 20 апреля 2026

---

## 📋 Что уже сделано

✅ Проведён исходный аудит `spring-panel` и `java-bot`  
✅ Проверена и скорректирована часть исходных выводов аудита  
✅ Зафиксирован roadmap рефакторинга в `docs/ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`  
✅ Начат и существенно продвинут рефакторинг transport-layer для `dialogs` и `settings`  
✅ Добавлен foundation-слой для UI runtime, preferences и page presets  
✅ Усилен `Phase 6` safety net через targeted unit/WebMvc/lifecycle/smoke tests  

---

## 🧭 Текущее состояние

Этот документ больше нельзя читать как “чистый список проблем на старте”. К
20 апреля 2026 часть наиболее болезненных рисков уже снижена в коде.

Что уже существенно улучшено:

- ранний UI bootstrap централизован через `fragments/ui-head.html`;
- `theme`, `ui-config` и operator UI preferences получили единый runtime-слой;
- controller-level split домена `dialogs` в основном выполнен:
  `DialogReadController`, `DialogListController`, `DialogWorkspaceController`,
  `DialogQuickActionsController`, `DialogMacroController`,
  `DialogAiOpsController`, `DialogWorkspaceTelemetryController`,
  `DialogTriagePreferencesController`;
- внутри giant `DialogWorkspaceService` уже начат service-level split:
  внешний профиль клиента, parity/composer-сборка, navigation/queue meta и
  rollout/meta-config, client segments/profile health и context blocks/health
  плюс client payload support и context sources/attribute policies вынесены в
  отдельные workspace sub-services; теперь туда же вынесен и
  `context contract`, а старый мёртвый review-control дубль удалён из
  самого workspace service;
- из giant `DialogService` уже вынесен первый самостоятельный client context
  read-layer: история обращений клиента, profile enrichment, profile match
  candidates и related events теперь живут в `DialogClientContextReadService`,
  а `workspace/macro` сценарии используют его напрямую;
- SLA/runtime дублирование между `DialogWorkspaceService` и
  `DialogListReadService` уменьшено через общий `DialogSlaRuntimeService`,
  который теперь держит lifecycle-state, deadline, minutes-left и config
  parsing для SLA-слоя;
- `settings` выведен из режима giant controller/update-method через
  `SettingsParametersController`, `SettingsItEquipmentController`,
  `SettingsUpdateService`, `SettingsDialogConfig*Service` и связанные subdomain
  services;
- bot runtime boundary начал уходить от жёсткого `spring-boot:run` в сторону
  launcher-strategy и explicit runtime contract;
- есть отдельные targeted тесты для sliced controllers, runtime contract,
  shared config/env foundation и page bootstrap.

Что остаётся главным архитектурным риском:

- `DialogService` всё ещё слишком крупный и остаётся главным кандидатом на
  service-level split, хотя первый client-context read slice из него уже
  выделен и часть потребителей переведена на новый слой;
- `DialogWorkspaceService` всё ещё крупный, хотя уже начал разгружаться через
  выделенные workspace sub-services и уже прикрыт targeted service tests по
  parity, navigation, rollout, client profile, context blocks, client payload,
  context source policy и context contract;
- но сам `workspace` уже заметно сузился: из него дополнительно убраны
  мёртвые helper-блоки по SLA/source-coverage/export formatting;
- `settings` всё ещё содержит remaining subdomains, которые могут снова
  разрастаться в общих слоях;
- `SharedConfigService` дублируется между `spring-panel` и `java-bot`;
- DTO/API contract и error contract всё ещё не унифицированы по проекту;
- persistence-слой по-прежнему смешивает raw JDBC и JPA/Repository подходы.

---

## 🔴 Критические проблемы (P0)

### 1. Нарушение принципа разделения ответственности между ботами

**Локация:**
- `java-bot/bot-telegram/src/main/java/com/example/supportbot/telegram/SupportBot.java`
- `java-bot/bot-vk/src/main/java/com/example/supportbot/vk/VkSupportBot.java`

**Суть:** Bot-модули всё ещё используют core-сервисы напрямую и остаются тесно
связаны с платформ-специфичной реализацией. Полного adapter boundary пока нет.

**Последствия:**
- сложно менять runtime/transport contract для отдельных платформ;
- трудно тестировать платформы изолированно;
- изменения в core продолжают иметь широкий blast radius.

**Решение:** Ввести явный platform-adapter boundary или эквивалентный runtime
contract между platform bot и core orchestration.

### 2. Отсутствие явного слоя DTO между Entity и API

**Локация:**
- `spring-panel/src/main/java/com/example/panel/entity/Ticket.java`
- `spring-panel/src/main/java/com/example/panel/controller/TaskApiController.java`
- `java-bot/bot-core/src/main/java/com/example/supportbot/entity/Ticket.java`

**Суть:** DTO/model-слой в проекте уже есть, но используется непоследовательно.
Часть API по-прежнему слишком близка к внутренней модели данных.

**Последствия:**
- нет стабильной типизированной защиты API контракта;
- усложняется версионирование и эволюция API;
- сложнее отделять persistence-изменения от transport-контрактов.

**Решение:** Ввести и закрепить единый DTO/API contract слой с маппингом.

### 3. Частичная централизованная обработка ошибок

**Локация:**
- `spring-panel/src/main/java/com/example/panel/config/RestExceptionHandler.java`
- runtime-границы между `spring-panel` и `java-bot`

**Суть:** Базовый обработчик уже есть, но единый error contract пока не
распространён на весь REST-слой и runtime boundary.

**Последствия:**
- разные сценарии всё ещё могут возвращать неодинаковые ошибки;
- сложнее строить стабильный API и мониторинг ошибок;
- интеграционные runtime-ошибки не всегда имеют единый формат.

**Решение:** Довести `@RestControllerAdvice`/error contract до уровня
кросс-доменного стандарта.

---

## 🟠 Высокий приоритет (P1)

### 4. Монолитные сервисы со слишком большой ответственностью

**Актуальный фокус:**
- `DialogService` остаётся главным giant service и основным SRP-риском;
- часть orchestration в `settings` уже разрезана, но remaining subdomains
  всё ещё требуют контроля.

**Важно:** `DialogApiController` больше не является главным transport-level
hotspot. Крупные controller-сценарии уже вынесены в отдельные controllers и
services, поэтому главный риск сместился в service layer.

**Решение:** Разрезать `DialogService` по bounded contexts:

```text
DialogService
  ├─ DialogListService
  ├─ DialogWorkspaceService
  ├─ DialogHistoryService
  ├─ DialogSlaService
  ├─ DialogAiService
  └─ DialogMapper / assembly layer
```

### 5. Дублирование кода между модулями

**Примеры:**
- `SharedConfigService` реализован отдельно в:
  - `java-bot/bot-core/src/.../service/SharedConfigService.java`
  - `spring-panel/src/.../service/SharedConfigService.java`

**Суть:** После transport/runtime рефакторинга этот риск стал ещё заметнее:
конфигурационный contract должен быть единым, а не “похожим в двух местах”.

**Решение:** Вынести общий config contract/module или хотя бы общий documented
shared config boundary.

### 6. Отсутствие интерфейсов для сервисов как системного правила

**Проблема:** Проект по-прежнему в основном опирается на concrete classes.
Это не самый срочный риск, но он усиливает связанность крупных доменов.

**Решение:** Вводить интерфейсы не механически для всего подряд, а на границах
bounded contexts, orchestration и integration layers.

### 7. Нарушение layered architecture в bot-модулях

**Проблема:** Bot-классы продолжают смешивать роли platform adapter,
transport handler и orchestration entrypoint.

**Решение:** Продолжать `Phase 5` через более явный runtime/platform boundary.

### 8. Неполное использование Spring Data JPA

**Проблема:** Внутри проекта сосуществуют raw `JdbcTemplate` и JPA/Repository.

**Суть риска:** Это уже не просто stylistic issue, а разные persistence-модели
внутри одного приложения, которые усложняют транзакции, тестирование и
эволюцию схемы.

**Решение:** Не делать большой bang refactor, а постепенно выравнивать
persistence boundaries по доменам.

---

## 🟡 Средний приоритет (P2)

### 9. Непоследовательное именование DTO/Model

DTO/model-слой существует, но naming и ответственность отличаются между
модулями и доменами.

### 10. Частично неформализованные Spring-конфигурации

Конфигурация приложения стала лучше формализована, но часть runtime/env
ожиданий всё ещё держится на implicit conventions и defaults.

### 11. Отсутствие сквозного API versioning

Часть маршрутов уже может быть стабилизирована, но единая стратегия
версионирования API по проекту так и не закреплена.

### 12. Ограниченное использование кэширования

Кэширование в проекте используется точечно и полезно, но не оформлено как
явная стратегия для горячих чтений.

### 13. Ограниченная доменная валидация

В части entity/model слоёв валидация всё ещё выражена слабо и часто живёт
не рядом с контрактом данных.

---

## 📊 Таблица соответствия правилам

| Правило | Статус | Комментарий |
|---------|--------|-------------|
| Layered Architecture | ⚠️ Частично | Controller-level split заметно улучшен, service-level split ещё не завершён |
| Dependency Inversion | ⚠️ Частично | На границах доменов стало лучше, но проект в целом всё ещё concrete-class heavy |
| Single Responsibility | ❌ НЕТ | Главный hotspot теперь `DialogService` и remaining giant services в `settings` |
| Don't Repeat Yourself | ❌ НЕТ | `SharedConfigService` и часть runtime contract still duplicated |
| SOLID Principles | ⚠️ Частично | Часть transport-layer нарушений снижена, но service boundaries ещё не доведены |
| Spring Best Practices | ⚠️ Частично | Улучшены bootstrap/runtime/test слои, но нужен единый error/API contract |

---

## 📈 Метрики качества

| Метрика | Текущее | Цель |
|---------|---------|------|
| Крупные controller hot spots | Сильно снижены | 0 giant controllers |
| Крупные service hot spots | Всё ещё есть | bounded services по доменам |
| Regression safety net | Targeted unit/WebMvc/lifecycle/smoke есть | широкий regression net |
| Code Coverage | Не формализована | 60%+ |
| Persistence consistency | Смешанный JDBC/JPA | явные domain boundaries |
| Shared runtime/config contract | Частично формализован | единый documented contract |

---

## 🎯 Актуальный план действий

### Фаза 1: Уже частично закрыта
- [x] Зафиксировать roadmap и начать поэтапный рефакторинг
- [x] Централизовать UI bootstrap и ownership UI preferences
- [x] Выполнить основной controller split для `dialogs/settings`

### Фаза 2: Текущий главный фокус
- [ ] Разрезать `DialogService` по bounded contexts
- [ ] Добить remaining `settings` subdomains
- [ ] Расширить safety net для следующих крупных рефакторингов

### Фаза 3: Следующий архитектурный уровень
- [ ] Унифицировать shared config/runtime contract между `spring-panel` и `java-bot`
- [ ] Довести DTO/API contract до системного правила
- [ ] Закрепить единый error contract и API governance

---

## 📁 Следующие шаги

1. Дожать service-level split `DialogService`
2. Добить remaining `settings` subdomains и persistence boundaries
3. Продолжить расширять `Phase 6` regression net
4. После этого возвращаться к shared-config unification и DTO/error contract

**Автор исходного аудита:** GitHub Copilot  
**Статус:** Документ актуализирован под состояние кода на 20 апреля 2026
