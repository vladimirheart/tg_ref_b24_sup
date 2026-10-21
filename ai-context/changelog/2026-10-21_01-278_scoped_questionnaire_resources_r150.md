# 01-278 — бизнес-локации и presets выбранного вопросника (R150)

Дата: 2026-10-21, Europe/Moscow. Общий статус задачи 🟡.

Промт пользователя:

> давай дальше большим пакетом

Действующее разрешение на фиксацию от предыдущего сообщения:

> продолжай. возьми более широкий пакет.
> коммит и пуш разрешаю сразу после внесения правок без моего подтверждения - разрешение только в течении этих суток

Разрешение действует только до 2026-10-22 00:00 +03:00. Сохраняется уточнение
пользователя: рабочий экземпляр проекта на другой машине, в другой сессии агента.
Здесь source/tests и isolated Testcontainers; рабочие runtime/DB/flags неизвестны.

- Baseline 2cd0713d2e7e21b25a4be41eaa67f077dbd27bcc, parent
  ccaa0396c3bc1ecb6ec9d0e2dc73f33f4097807c; R149 actual 30 files проверен.
  Промт: «давай дальше большим пакетом». Рабочий экземпляр на другой машине, в другой сессии;
  текущая — source/tests. Commit/push разрешены до 2026-10-22 00:00 +03:00.
- Активные business_locations читаются по точному business_id. Global/iiko/
  legacy location tree и dialog_config.preset_definitions не участвуют.
  Business presets хранятся в businesses.settings.questionnaire_presets как
  group → field → список строк. Names/labels не определяют owner. GENERAL
  возвращает только свой вопросник и два пустых объекта ресурсов.
- System-admin GET/PUT /api/business-admin/businesses/{businessId}/questionnaire-presets
  требует PAGE_SETTINGS + ROLE_ADMIN, foundation и CSRF для PUT. READ/ADMIN
  бизнеса или ALL_BUSINESSES не заменяют системные полномочия. expectedSha256
  и row lock защищают от потери параллельного изменения. Другие settings
  сохраняются; отсутствующий/архивный бизнес даёт 404, stale version — 409.
- V3 /internal/api/bot/channels/{channelId}/questionnaire/v3/choices и selection
  используют подпись path/query/forStart и ACK=3 для starts. Каждый choice
  содержит resourcesSha256. Хешируется UTF-8 JSON [locationTree,presetDefinitions]
  с рекурсивной сортировкой object keys и сохранением порядка массивов; общий
  catalog SHA фиксирует все choices и resource versions. ID/SHA самого шаблона
  не подменяется материализованным flow. V1/V2 не содержат нового nullable
  digest в backend JSON и по-прежнему не выдают presets; SDK поддерживает их
  сохранённые сессии, новые starts общего каталога запрашивает только через V3.
- SDK проверяет resource SHA по сохранённому choice, точный owner, shape,
  положительные/уникальные location IDs, допустимые поля, группы и значения.
  Для location_type/city/location_name разрешена одна reference на dimension;
  порядок вопросов произвольный, уже выбранные dimensions взаимно ограничивают
  варианты. Остальные business presets — независимые списки строк. Exclusions
  сохраняются, пустой список не становится свободным вводом и не открывает
  global fallback. Лимиты: 1000 активных локаций, 20 групп × 20 полей, до 100
  строк на поле и до 1000 строк суммарно. Каталог пока до 100 choices.
- MAX/VK сохраняют весь выбранный resource snapshot в Selection; Telegram —
  в уже существующем SelectedQuestionnaire. Restore проверяет menu/owner/hash.
  В V3 answer key — явный binding_key либо ID вопроса; прежняя legacy/preset
  эвристика MAX/VK для старых сессий сохранена. Повтор после неизвестного
  результата queue использует ту же команду/ответы/publication key. GENERAL V3
  переживает JSON/rebuild без синтетического business ID и без чужих ресурсов.
- До нового root/child INSERT writer повторно проверяет полный current catalog,
  шаблон и resource version. V3 preset attributes проверяются по question ID,
  binding key, input type, text/label, допустимым значениям и согласованным
  dimensions; duplicate/unknown attributes и несовпадающие непустые legacy
  projections отклоняются. Exact replay существующего root сохраняет frozen
  proof и не перечитывает новый справочник. Полная server-side проверка всего
  route/required question path этим шагом не объявляется готовой.
- Validation: 765 Java tests / 79 XML, failures/errors/skips=0;
  backend 215/11, полный fleet 550/68.
  Реальные PostgreSQL/Spring transaction/inbox/rollback, CAS concurrency, signed
  HTTP/ACK, отдельный Node-вычисленный digest vector в двух Java-модулях,
  nullable GENERAL, provider ingress/JSON/CAS и frozen retries проверены.
  Рабочая БД и реальные providers не использовались; новая migration не нужна.
- Промежуточные результаты сохранены в .tmp/r150-*. Первый backend: 201 тест,
  шесть ошибок нового fixture из-за повторной вставки одинаковой локации в общей
  test DB; исправлен test upsert без ослабления unique constraint/assertions.
  Первый fleet: SDK/Telegram GREEN, два новых VK assertions выявили legacy
  fallback ключей к preset.field; для V3 MAX/VK исправлены на ID/binding_key.
  Следующий MAX прогон выявил ошибочное ожидание HTTP status в новом queue-retry
  тесте: фактический контракт бросает исключение; тест проверяет это исключение
  и идентичность двух команд. До Maven исправлен пропущенный semicolon в emitted
  MAX fixture. Unsupported menu version cases используют 4 после поддержки 1–3;
  остальные прежние assertions сохранены. Отдельно добавлены симметричные location filters для любого
  порядка вопросов и серверная проверка ответов; повторены зависимые suites,
  неизменённые GREEN reports сохранены. Generated Sass CSS исключён из пакета.
- Источники/документация — 32 файла. Source apply, commit и push разделены,
  перед ними проверяются actual remote, exact set и hashes, а также дата grant.
  Рабочие Docker/DB/flags/deploy/restart/backfill не затронуты. Отдельный UI,
  >100 choices, scoped media/feedback/edit, live Redis/provider/proxy, retention,
  global/system/task policy, S2B и rollout остаются PENDING. Общий статус 🟡.


Контракт настройки через API: GET возвращает businessId, sha256, groups; PUT
передаёт expectedSha256 из GET и groups. Например, собственный список бизнеса:

```json
{"expectedSha256":"<sha256 из GET>","groups":{"department":{"name":["Кухня","Доставка"]}}}
```

Reference в отдельном business template:

```json
{"id":"department-question","type":"preset","preset":{"group":"department","field":"name"}}
```

Вопросы location presets используют group=locations и field=location_type/city/
location_name; бизнес уже выбран явно. Не редактировать legacy/global preset
definitions для подключения этих источников. UI этих настроек ещё в очереди.
