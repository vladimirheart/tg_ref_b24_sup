# Iguana

![Логотип Iguana](spring-panel/src/main/resources/static/iguana-icon.svg)

Iguana — support CRM и операторская панель для обработки обращений из
`Telegram`, `VK`, `MAX` и внешних форм. Проект объединяет многоканальный
приём сообщений, рабочее место операторов, базу знаний, аналитику и управление
ботами из единого интерфейса.

## Что есть в проекте

- многоканальные диалоги с клиентами через `Telegram`, `VK`, `MAX` и web-формы;
- рабочее место оператора со списком диалогов, SLA, историей сообщений,
  аватарами и уведомлениями;
- карточки клиентов с источником обращения и идентификаторами по каналу;
- база знаний с ручным редактированием и импортом статей из `Notion`;
- дашборды и аналитические отчёты по каналам, сотрудникам, бизнесам и локациям;
- настройки каналов, ролей, локаций, справочников и визуальной темы панели;
- запуск и мониторинг процессов ботов прямо из `spring-panel`;
- мониторинг SSL-сертификатов и дополнительные сервисные уведомления.

## Архитектура

Проект состоит из двух основных частей:

- `spring-panel/` — веб-панель на `Spring Boot` с UI, настройками, аналитикой,
  базой знаний, клиентами, диалогами и API для operator workflow;
- `java-bot/` — набор Java-ботов и общее ядро обработки сообщений:
  `bot-core`, `bot-telegram`, `bot-vk`, `bot-max`.

Общие настройки и часть справочников лежат в `config/shared/`.

## Основные сценарии

### Диалоги и операторы

- входящие сообщения автоматически создают или продолжают диалоги;
- оператор видит канал, SLA, ответственного, историю переписки и архив
  предыдущих обращений клиента;
- ответы клиенту отправляются из панели в соответствующий канал;
- при событиях по диалогу используются in-app уведомления в sidebar.

### Клиенты

- карточка клиента показывает не только условный "Telegram ID", а реальный
  источник и идентификатор в конкретном канале;
- поддерживаются статусы клиента, телефоны, usernames, блокировки и история.

### База знаний

- статьи можно вести внутри панели;
- есть внутренняя интеграция `Notion` для импорта статей по авторам в базу
  знаний проекта;
- для контента используется markdown-oriented workflow.

### Настройки и инфраструктура

- из панели управляются каналы, боты, шаблоны, справочники, роли и оргструктура;
- для каналов доступны запуск и контроль bot-процессов;
- поддерживается конфигурирование внешних интеграций и сетевых подключений.

## Быстрый старт

### Windows

Подробный гайд: [docs/windows_setup.md](docs/windows_setup.md)

Базовый сценарий:

```powershell
cd spring-panel
.\run-windows.bat
```

Если нужно передать параметры:

```powershell
$env:JAVA_OPTS='-Xmx1024m'
$env:SPRING_OPTS='--server.port=8080'
.\run-windows.bat
```

### Linux

```bash
cd spring-panel
export JAVA_OPTS="-Xmx1024m"
export SPRING_OPTS="--server.port=8080"
./run-linux.sh
```

По умолчанию панель поднимается на <http://localhost:8080/>.

## Конфигурация

Проект использует переменные окружения и shared JSON-конфиги.

1. Подготовьте `.env` с основными параметрами.
2. Проверьте файлы в `config/shared/`.
3. Запустите `spring-panel`.
4. Настройте каналы и ботов через UI.

Ключевые точки конфигурации:

- `TELEGRAM_BOT_TOKEN` и другие токены каналов;
- `APP_DB_*` для основных БД;
- `APP_BOT_DATABASE_DIR` для отдельных bot-баз;
- `config/shared/settings.json`;
- `config/shared/locations.json`;
- `config/shared/org_structure.json`.

Полный список переменных: [docs/environment_variables.md](docs/environment_variables.md)

## Хранилища данных

В проекте используется разделение по SQLite-файлам:

- `tickets.db` — диалоги, сообщения, активные обращения;
- `users.db` — пользователи панели, роли и аватары;
- `clients.db` — профили клиентов;
- `knowledge_base.db` — база знаний;
- `objects.db` — объекты и связанные справочники;
- `settings.db` — общие настройки и часть служебных связей;
- `bot-<channelId>.db` — отдельные базы каналов/ботов.

Подробнее: [docs/database-paths.md](docs/database-paths.md)

## Запуск и управление ботами

Управление ботами вынесено в панель:

1. Откройте `Настройки -> Каналы (боты)`.
2. Выберите канал.
3. Запустите нужный bot-процесс.

Панель умеет:

- создавать и регистрировать bot-базы;
- запускать Java-процессы ботов;
- отслеживать статусы и отображать их в интерфейсе;
- использовать общий runtime-конфиг для `Telegram`, `VK` и `MAX`.

Технические детали:

- [docs/java_bot.md](docs/java_bot.md)
- [docs/vk_bot_setup.md](docs/vk_bot_setup.md)
- [docs/max_bot_setup.md](docs/max_bot_setup.md)

## UI и рабочее место

В последних обновлениях проект получил более цельный UI:

- переработанный sidebar;
- обновлённую страницу dashboard;
- улучшенный список диалогов с цветами каналов и SLA;
- более аккуратные settings-модалки и карточки;
- аватары операторов в рабочих сценариях;
- уведомления в колокольчике sidebar.

Если нужен контекст по UI-изменениям:

- [docs/ui_ux_audit.md](docs/ui_ux_audit.md)
- [docs/settings_page_gap_audit.md](docs/settings_page_gap_audit.md)
- [docs/ai_agent_uiux_changes_plan.md](docs/ai_agent_uiux_changes_plan.md)

## Документация

### Запуск и конфиг

- [docs/configuration.md](docs/configuration.md)
- [docs/environment_variables.md](docs/environment_variables.md)
- [docs/windows_setup.md](docs/windows_setup.md)
- [docs/database-paths.md](docs/database-paths.md)

### Боты и каналы

- [docs/java_bot.md](docs/java_bot.md)
- [docs/vk_bot_setup.md](docs/vk_bot_setup.md)
- [docs/max_bot_setup.md](docs/max_bot_setup.md)
- [docs/conversation_flow.md](docs/conversation_flow.md)

### Архитектура и развитие

- [docs/ARCHITECTURE_AUDIT_2026-04-08.md](docs/ARCHITECTURE_AUDIT_2026-04-08.md)
- [docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md](docs/ARCHITECTURE_AUDIT_VALIDATION_2026-04-09.md)
- [docs/EXEC_SUMMARY_ARCHITECTURE_AUDIT.md](docs/EXEC_SUMMARY_ARCHITECTURE_AUDIT.md)
- [docs/REFACTORING_PLAN_2026.md](docs/REFACTORING_PLAN_2026.md)

### Формы, AI и база знаний

- [docs/public_forms_implementation_plan.md](docs/public_forms_implementation_plan.md)
- [docs/runbooks/public_forms_incidents.md](docs/runbooks/public_forms_incidents.md)
- [docs/ai_dialog_agent_plan.md](docs/ai_dialog_agent_plan.md)
- [docs/ai_solution_memory_editing.md](docs/ai_solution_memory_editing.md)

## Репозиторий в работе

Проект активно развивается, поэтому часть документов в `docs/` описывает не
только текущее поведение, но и планы, аудит или runbook-сценарии. Для
прикладной разработки обычно достаточно начать с:

1. `README.md`
2. [docs/configuration.md](docs/configuration.md)
3. [docs/java_bot.md](docs/java_bot.md)
4. [docs/database-paths.md](docs/database-paths.md)
