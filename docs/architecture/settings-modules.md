# Границы модулей настроек и мониторинга

Рефакторинг 01-330 выполнен от `90f2a54f21f93a0fddbbfc9b6f57d987cc97119a`.
Он сохраняет API, очередь мониторинга, модель прав и данные. Production находится
на другой машине; локальные проверки не являются production acceptance.

| Владелец | Ответственность | Внутренние компоненты |
| --- | --- | --- |
| `auth-management.js` | Загрузка, состояние, capabilities, подписки, внешние hooks | photo, user, roles, organization |
| `bot-settings.js` | Lifecycle страницы, канонические шаблоны, сохранение | model, rating, editor |
| `RmsLicenseMonitoringService` | Очередь, lifecycle, транзакции, DTO/API | RmsLicenseClient, RmsTextCodec, RmsNetworkProbe |

Контроллеры пользователей получают host context; model/editor вопросников читают
состояние через live getters/setters. Старые точки входа сохраняются фасадами.
Фабрики не трогают DOM и не читают ещё не инициализированные значения host.
Шесть перекрытых объявлений в bot-settings удалены с сохранением последней,
фактически исполнявшейся версии. Пробелы внутри строк и template literals сохранены.

Общие правила `body[data-ui-page]` сведены в `app/_ui-foundation.scss`.
Уведомления, controls и resize выделены из style/base с сохранением порядка CSS.
Кнопка закрытия имеет общего владельца `style/_close-button.scss`; светлая иконка
в тёмной теме больше не инвертируется повторно. У карточки вопроса убрана тень.
Остальные проверенные вычисленные стили совпадают с исходными. Версии четырёх
изменённых CSS обновлены во всех использующих их шаблонах.

## Проверки

- 262 targeted Java tests: auth/API/identity, права страниц/настроек, lifecycle,
  RMS и source contracts, реальный Thymeleaf fragment.
- AST-сравнение: 144 auth methods, 115 bot functions; состояние и регистрация
  обработчиков совпадают с исходными; удалены только шесть перекрытых объявлений.
- RMS: 74 одинаковых результата старого и нового сервиса, включая XML/XXE,
  лицензии, адреса, кодировки, команды и HTTP через локальный fixture server.
- Edge: 61 credential cases, 12 session cases, 12 permission-editor scenarios,
  4 question-editor contexts, 12 organization/user-card scenarios.
- CSS: 32 сравнения (8 страниц × 2 темы × 2 ширины), кроме перечисленных выше
  намеренных изменений вычисленные значения совпали.
- Node: 197 tests, 190 pass; те же 7 failures независимо воспроизведены до правок
  (194 tests, 187 pass). Исходные падения: dialog-ai-query-requests-r54,
  dialog-details-query-api-r50, dialog-query-navigation-r48,
  dialog-read-query-requests-r53, dialogs-secondary-exact-legacy-ticket-id-r29,
  incidents-transport-exact-ticket-id-r34 и один cache-version case
  incident-descriptions-r13. Эти тесты и их реализации не менялись.

Доказательства, снимки и exact file/hash manifest сохраняются в ignored
`spring-panel/target/architecture-330/` и соседних QA-каталогах. Применение
проверенных файлов фиксирует `architecture-330-apply.json` отдельно от сборки.
Полный Node suite имеет перечисленные исходные ошибки; общий статус GREEN
означает успешные проверки этого изменения и отсутствие новых ошибок.

Команды проверок: `node --test spring-panel/src/test/js/settings-module-composition-r330.test.js`,
`node tools/permission-editors-qa.cjs`, `node tools/organization-users-qa.cjs`,
`node tools/question-editor-qa.cjs`, `node tools/shared-ui-style-qa.cjs`.
Для browser tools нужны Playwright/Edge через переменные, описанные в самих tools;
users fragment создаёт `PermissionEditorsRenderTest`. Credentials/sessions tools
используют локальный CDP, не работающую панель.

Архивы и восстановление: [recovery-archive.md](recovery-archive.md).
