# Целевая topology SQLite-баз

Этот документ фиксирует target-state архитектуру SQLite-слоя проекта. Он описывает не только текущее состояние файлов, а финальную логическую модель, к которой должны вести последующие migration-задачи.

Текущее transitional-состояние и legacy-совместимость остаются описанными в `docs/database-paths.md` и `ai-context/rules/backend/04-sqlite-topology.md`.

## Резюме решения

Целевая topology проекта должна состоять из `5` логических SQLite-контуров:

| Логический контур | Целевой файл | Назначение | Почему отдельный файл оправдан |
| --- | --- | --- | --- |
| `panel-runtime` | `panel_runtime.db` | Каноническая operational-база панели: диалоги, заявки, задачи, каналы, клиентский/knowledge/object reference-контур, AI runtime state | Это главный business source of truth панели; здесь живут данные, вокруг которых строится UI, workflow и operator-read path |
| `panel-identity` | `panel_identity.db` | Пользователи панели, роли, права, парольный и session-контур | Auth/identity имеют отдельный lifecycle, доступы, backup-политику и не должны смешиваться с диалоговой историей |
| `monitoring` | `monitoring.db` | Конфигурация мониторов, raw history проверок, monitoring rollups и retention-данные | Monitoring пишет часто, растёт не как бизнес-история и требует короткого raw retention |
| `panel-telemetry` | `panel_telemetry.db` | UI/runtime telemetry, technical audit, AI event log, служебные агрегаты по техничке | Высокочастотная техническая история не должна раздувать main operational DB и должна чиститься по отдельной policy |
| `bot-runtime` | `bot_runtime.db` | Bot-side transport/runtime контур: bot users, ingress/delivery history, applications и channel runtime state | Интеграционный bot-контур имеет другой профиль записи и не должен быть скрытым хвостом panel-runtime |

Per-channel файлы `bot-<channelId>.db` в target-state не считаются отдельными bounded-контекстами. Если они остаются по operational-причинам, это допустимо только как шардирование внутри логического контура `bot-runtime`, а не как параллельный source of truth рядом с shared bot DB.

## Канонические доменные границы

### `panel_runtime.db`

Здесь канонически живут:

- `tickets`, `messages`, `chat_history`
- `ticket_*`, `task_*`, `tasks`
- `notifications`, `channel_notifications`, `pending_feedback_requests`
- `channels`, `app_settings`, `settings_parameters`
- `client_*`
- `knowledge_*`, `it_equipment_catalog`
- `objects`, `object_passports`
- AI runtime state и knowledge, относящиеся к самому panel-flow:
  - `ticket_ai_agent_state`
  - `ticket_ai_agent_dialog_control`
  - `ai_agent_solution_memory`
  - `ai_agent_solution_memory_history`
  - `ai_agent_knowledge_unit`
  - `ai_agent_memory_link`
  - `ai_agent_intent_catalog`
  - `ai_agent_intent_policy`
  - `ai_agent_sensitive_patterns`
  - `ai_agent_suggestion_feedback`

Логика: это единый business/read-model панели. Всё, что регулярно участвует в карточке диалога, списке, SLA-flow, knowledge/operator workflow и клиентском контексте, не должно расползаться по множеству SQLite-файлов без сильной причины.

### `panel_identity.db`

Здесь канонически живут:

- `panel_users` / `users`
- `roles`
- `user_authorities`
- `password_reset_requests`
- `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`, если JDBC session storage остаётся в SQLite

Логика: identity/access-контур имеет отдельный lifecycle, иной backup/sensitivity профиль и должен масштабироваться независимо от массы диалоговых/мониторинговых данных.

### `monitoring.db`

Здесь канонически живут:

- `ssl_certificate_monitors`
- `rms_license_monitors`
- `iiko_api_monitors`
- `monitoring_check_history`
- будущие `monitoring_*_hourly`, `monitoring_*_daily`, `monitoring_*_monthly` rollup-таблицы
- retention/state-таблицы самого monitoring-контура

Логика: monitoring-история по частоте записи и по бизнес-ценности радикально отличается от истории диалогов. Это отдельный технический контур, который должен жить своей retention-политикой.

### `panel_telemetry.db`

Здесь канонически живут:

- `dialog_action_audit`
- `workspace_telemetry_audit`
- `ai_agent_event_log`
- будущие telemetry/audit rollup-таблицы
- технические event-очереди и диагностические таблицы, не являющиеся business facts

Не должны жить здесь:

- ticket/business state (`tickets`, `ticket_ai_agent_state`)
- канонические operator decisions, если они нужны бессрочно как бизнес-факты

Логика: всё, что важно в сыром виде лишь ограниченный горизонт и существует ради диагностики, UX analytics, troubleshooting или AI observability, должно жить вне main panel-runtime.

### `bot_runtime.db`

Здесь канонически живут:

- `bot_users`
- `bot_chat_history`
- `applications`
- transport/runtime-состояние каналов, если оно нужно bot-side процессам

Логика: bot-runtime должен быть интеграционным ingress/transport-контуром. Как только часть данных проецирована в `panel_runtime.db` и перестаёт быть bot-only источником истины, её retention может быть короче, чем у panel business facts.

## Что должно исчезнуть из текущей раскладки

| Текущий файл | Решение в target-state |
| --- | --- |
| `tickets.db` | Переименовать в `panel_runtime.db`; перестать использовать имя, будто это частная DB только для тикетов |
| `users.db` | Переименовать в `panel_identity.db` |
| `bot_database.db` | Переименовать в `bot_runtime.db` |
| `settings.db` | Удалить как отдельный контур; `database_registry`, `database_links`, `bot_instances` либо убрать совсем, либо держать как low-volume admin metadata внутри `panel_runtime.db`, но не как отдельную БД |
| `clients.db` | Поглотить в `panel_runtime.db`; отдельная клиентская БД не оправдана при текущей domain coupling и объёмах |
| `knowledge_base.db` | Поглотить в `panel_runtime.db`; knowledge тесно связана с operator workflow и не требует отдельного файла |
| `objects.db` | Поглотить в `panel_runtime.db`; текущий объём маленький, а cross-flow связность выше выгоды от split |
| `monitoring.db` | Оставить как отдельную canonical DB и довести до полной ownership-модели monitoring-контура |
| `bot-<channelId>.db` | Не считать отдельными domain DBs; либо убрать, либо трактовать как внутренние shard-файлы одного `bot-runtime` контура |

## Почему итоговое число БД именно `5`

`5` логических БД дают чистый баланс между domain clarity и operational simplicity:

- меньше `5` плохо, потому что auth, monitoring и high-write telemetry начинают конкурировать с основным panel-runtime за размер, retention и режим обслуживания;
- больше `5` плохо, потому что появляются artificial splits (`clients`, `knowledge`, `objects`, `settings`), которые не несут отдельного operational выигрыша, но плодят bootstrap, wiring и migration drift;
- `panel-runtime` остаётся единым business центром панели;
- `panel-identity`, `monitoring`, `panel-telemetry` и `bot-runtime` получают отдельные lifecycle/retention-политики, действительно отличающиеся по профилю.

## Retention и lifecycle policy

| Категория данных | Канонический контур | Политика хранения |
| --- | --- | --- |
| Raw business facts | `panel_runtime.db` | Бессрочно, если нет отдельной бизнес-политики на архивирование |
| Raw bot transport history | `bot_runtime.db` | `90-365` дней; всё, что уже канонически спроецировано в panel-runtime и не нужно для reconciliation, не хранить бессрочно |
| Raw monitoring history | `monitoring.db` | `30-90` дней, default `30` |
| Raw telemetry / technical audit | `panel_telemetry.db` | `30-90` дней, default `60`; если событие нужно дольше, его следует поднимать в business event, а не держать как raw telemetry |
| Hourly rollups | `monitoring.db`, `panel_telemetry.db` | `90-180` дней |
| Daily rollups | `monitoring.db`, `panel_telemetry.db` | `1-3` года |
| Monthly rollups | `monitoring.db`, `panel_telemetry.db` | `3-7` лет |
| Долгоживущие бизнес-агрегаты | `panel_runtime.db` | Бессрочно или по отдельной продуктовой/регуляторной политике |

### Что относится к “чистой техничке”

Ни в raw, ни в длинных rollup-хвостах не нужно держать годами:

- `monitoring_check_history.details_excerpt`
- ping/traceroute raw payloads
- response excerpts и отладочные HTTP/SSL куски
- UI clickstream-style telemetry
- низкоуровневый AI event trace, нужный только для troubleshooting

Если на длинном горизонте нужна аналитика, нужно сохранять не сырой payload, а агрегаты: counts, rates, failure windows, percentile latency, escalation share, operator workload trends.

## Budget роста по основным контурам

Оценка ниже не претендует на точный capacity-plan, но задаёт правильный порядок величин для SQLite.

### `panel_runtime.db`

Базовое допущение:

- `300` новых обращений в сутки
- `10-20` записей истории на одно обращение с учётом клиента, оператора и системных событий

Порядок роста:

- `tickets`: около `9 000` строк в месяц, `108 000` в год
- `chat_history`: около `90 000-180 000` строк в месяц, `1.1-2.2` млн в год
- `messages` и ticket-adjacent state: на порядок `tickets`, а не `chat_history`

Практический вывод:

- сам по себе panel-runtime ещё подходит для SQLite;
- главный риск здесь не raw throughput, а рост индексов и длинной истории диалогов;
- если средний payload сообщения + индексы дают хотя бы `0.7-1.5 KB` на запись, `chat_history` легко выйдет на `75-270 MB` в месяц и `0.9-3.2 GB` в год.

### `monitoring.db`

Этот контур растёт быстрее всех.

Если мониторинговых целей порядка `200-300`, а проверки идут раз в `5-15` минут, raw history легко выходит на:

- сотни тысяч строк в неделю
- миллионы строк в месяц

В репозитории уже был operational сигнал: исторически `33 275` строк `monitoring_check_history` вместе с другими техлогами заметно раздували общую БД, а verbose `details_excerpt` делал стоимость строки существенно выше, чем у компактного business event.

Практический вывод:

- raw history должна жить отдельно от panel-runtime;
- aggressive retention здесь не optional, а обязательна;
- длинный горизонт нужен только в агрегированном виде.

### `panel_telemetry.db`

Для operator actions, workspace telemetry и AI events можно ожидать:

- `5-15` технических событий на одно обращение в активных сценариях
- порядок `45 000-135 000` строк в месяц при `300` обращениях в сутки

Это обычно меньше monitoring-контура, но достаточно много, чтобы без отдельной retention-политики раздувать main DB техническим шумом.

### `bot_runtime.db`

Порядок роста близок к ingress-нагрузке каналов:

- `applications`: примерно на уровне новых обращений
- `bot_chat_history`: на уровне transport-сообщений до projection в panel-runtime

Если этот контур остаётся каноническим только для transport/runtime нужд, его размер должен сдерживаться reconciliation-политикой и TTL, а не бессрочным накоплением дублей.

### `panel_identity.db`

Рост медленный и легко помещается в SQLite:

- пользователи, роли, authority-записи, сессии, password reset
- критичнее не объём, а backup/access discipline

## Migration plan высокого уровня

1. Зафиксировать логические имена контуров и ввести будущие env-aliases:
   - `APP_DB_PANEL_RUNTIME`
   - `APP_DB_PANEL_IDENTITY`
   - `APP_DB_PANEL_TELEMETRY`
   - `APP_DB_MONITORING`
   - `APP_DB_BOT_RUNTIME`
2. Сохранить backward compatibility:
   - старые `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT` и другие legacy-переменные работают как alias хотя бы один migration-cycle;
   - runtime явно логирует, какой logical contour и какой physical path был выбран.
3. Завершить вынос monitoring history в `monitoring.db` и ввести cleanup/rollup policy по задаче `01-140`.
4. Создать отдельный `panel_telemetry.db` и перевести туда:
   - `dialog_action_audit`
   - `workspace_telemetry_audit`
   - `ai_agent_event_log`
5. Перестать считать `settings.db`, `clients.db`, `knowledge_base.db`, `objects.db` каноническими runtime-базами:
   - нужные low-volume данные перенести в `panel_runtime.db`;
   - пустые/дублирующие secondary-файлы удалить после проверки.
6. Зафиксировать `bot_runtime.db` как единственный canonical bot contour:
   - shared DB остаётся единственным source of truth, либо per-channel shard-слой прячется за единым runtime abstraction;
   - panel-read path не должен одновременно зависеть и от shared, и от per-channel хранения.
7. После того как код отвязан от legacy file naming, выполнить физические rename:
   - `tickets.db -> panel_runtime.db`
   - `users.db -> panel_identity.db`
   - `bot_database.db -> bot_runtime.db`
8. Удалить transitional wiring и устаревшие registry-артефакты:
   - `settings.db`
   - legacy registry tables, если они больше не нужны даже как diagnostics mirror
   - старые env-переменные после запланированного compatibility окна

## Правила для новых задач до завершения миграции

- Новые bounded-контексты нельзя автоматически выносить в отдельный SQLite-файл без явного роста/retention/operational обоснования.
- Всё, что является technical history, по умолчанию должно проектироваться либо в `monitoring.db`, либо в `panel_telemetry.db`, а не в `panel_runtime.db`.
- Всё, что является operator-facing business source of truth, по умолчанию должно усиливать `panel_runtime.db`, а не плодить `clients.db` / `knowledge_base.db` / `objects.db`-подобные split-файлы.
- Физическое текущее имя файла ещё может быть legacy, но архитектурное решение для новых изменений должно приниматься по logical contour, а не по историческому filename.
