# UI/UX аудит панели Iguana

**Дата пересмотра:** 23 марта 2026 года.

## Зачем обновлён этот документ
Предыдущая версия аудита была полезна как стратегический взгляд, но местами смешивала:
- реально существующие UI/API-механизмы;
- сценарии, подтверждённые тестами и конфигурацией;
- целевое production-состояние, которое ещё не доказано эксплуатацией.

Ниже — более приземлённая версия аудита: что действительно видно в текущем репозитории, что можно считать сильной стороной продукта уже сейчас и где остаётся разрыв между «фича есть в коде» и «процесс устойчив в реальной эксплуатации».

## Методика этого обновления
Документ пересмотрен по текущему состоянию репозитория, а не по историческому roadmap. За основу взяты:
- шаблон страницы диалогов и клиентский JS;
- API-контракт диалогов/workspace/macro/telemetry;
- серверная SLA-orchestration логика;
- аналитический экран и сопутствующие настройки;
- runbook для rollback `workspace_v1`;
- WebMvc/сервисные тесты, подтверждающие ожидаемое поведение спорных сценариев.

Это важно: часть выводов ниже подтверждена UI и серверным кодом напрямую, а часть — тестами и конфигурацией. Поэтому в аудите отдельно отмечены области, где функциональность уже реализована, но ещё не выглядит полностью доказанной как production-standard.

---

## Executive summary
Панель уже нельзя описывать как «простую CRM-страницу со списком обращений». В текущем состоянии у продукта есть четыре сильных пласта:

1. **Сильный triage-слой в списке диалогов.**
   Есть быстрый поиск, сохранённые представления, SLA-first сценарии, bulk actions, горячие клавиши и быстрые действия без открытия карточки.

2. **Отдельный `workspace_v1` как реальный рабочий контур, а не макет.**
   В коде есть shell, API-контракт, telemetry, fallback в legacy modal и rollback-процедура.

3. **Серверный слой orchestration вокруг SLA и rollout.**
   Продукт уже опирается не только на фронтовый UI, но и на серверные решения: webhook-эскалации, режимы monitor/assist/autopilot, auto-assignment, scorecard и governance packet.

4. **Попытка превратить UX в управляемую систему, а не просто набор экранов.**
   Это видно по analytics-экрану, gap breakdown, KPI-gate, policy-аудитам и macro governance обзору.

Главный вывод: **базовая платформа уже собрана**, но основной риск сейчас — не отсутствие новых функций, а то, что многие сильные механизмы находятся в переходном состоянии между «есть в коде» и «регулярно доказаны в эксплуатации».

---

## Что действительно реализовано и выглядит убедительно

### 1. Список диалогов и triage — сильная часть продукта уже сейчас

#### Что есть в UI
На экране диалогов уже присутствуют:
- быстрый поиск;
- выбор размера страницы;
- набор готовых представлений: **Все**, **Активные**, **Новые**, **Без ответственного**, **Просроченные**, **SLA критичные**, **Нужна эскалация**;
- фильтр по окну SLA;
- режим сортировки **стандарт / SLA-first**;
- отдельные модальные сценарии для фильтров, колонок, горячих клавиш и экспериментальной информации;
- bulk-toolbar с действиями **назначить**, **отложить**, **закрыть**;
- быстрые действия внутри workspace и списка.

#### UX-оценка
Это уже хороший операторский triage-интерфейс, потому что он закрывает три ключевых задачи:
- быстро найти нужный тикет;
- быстро отфильтровать очередь по риску;
- быстро применить типовые действия без глубокого перехода в карточку.

#### Что ещё мешает считать контур полностью «зрелым»
- нет явного доказательства, что все views действительно сохранены на сервере как полноценные persistent saved views; фактически это скорее **предустановленные рабочие режимы интерфейса**;
- часть удобства держится на клиентском JS и local storage, а не на централизованной пользовательской настройке;
- нужен отдельный UX-review на перегруженность тулбара: возможностей много, но onboarding нового оператора может быть тяжёлым.

**Статус:** **реализовано хорошо, требует упрощения ментальной модели и проверки discoverability**.

---

### 2. `workspace_v1` — уже полноценный рабочий режим, но ещё не окончательно «единственный»

#### Что подтверждается кодом
`workspace_v1` — это не заглушка. В нём уже есть:
- отдельный shell поверх списка диалогов;
- заголовок и контекст карточки;
- навигация по очереди (**предыдущий / следующий**);
- быстрые действия (**назначить мне / отложить / закрыть / переоткрыть**);
- composer с отправкой текста, медиа, черновиками и reply target;
- секция макросов внутри composer;
- правая колонка с блоками **клиент / история / связанные события / SLA / категории**;
- отдельные loading/error состояния по каждому блоку;
- баннеры rollout/parity/readonly;
- явная кнопка перехода в **Legacy modal**;
- серверный workspace-контракт `workspace.v1`.

#### Что это означает с UX-точки зрения
Это уже не «альтернативная карточка», а **попытка построить основной операторский стол**, где навигация, ответ, контекст и оперативные действия собраны в одном месте.

Сильная сторона решения — уменьшение переключений:
- список даёт triage;
- workspace даёт обработку;
- analytics даёт наблюдаемость за rollout.

#### Где остаётся разрыв
При всей зрелости реализации продукт всё ещё сохраняет dual-run модель:
- legacy modal не удалён;
- rollback runbook прямо описывает возврат на legacy flow без деплоя;
- telemetry и parity-механика существуют именно потому, что полная эквивалентность ещё должна подтверждаться.

Отсюда главный UX-риск: операторская модель пока не до конца однотипна. Для части сценариев система всё ещё допускает мысль «если workspace подведёт, вернёмся в modal». Это хорошо для надёжности rollout, но плохо для финальной простоты продукта.

**Статус:** **функционально силён и близок к primary-flow, но организационно всё ещё transition-state**.

---

### 3. SLA и приоритизация — это уже не просто бейджи

#### Что подтверждается реализацией
В проекте есть не только SLA-индикация на уровне списка, но и более серьёзный orchestration-слой:
- расчёт target/warning/critical порогов из `dialog_config`;
- формирование server-side SLA signals по тикетам;
- критичность и pin/escalation logic для нераспределённых тикетов;
- webhook-эскалация;
- fan-out по нескольким webhook endpoint;
- cooldown, timeout, retry/backoff;
- режимы orchestration: **monitor / assist / autopilot**;
- auto-assign / redirect;
- route-based policy snapshot для workspace и аналитики.

Судя по тестам, поддерживаются и более сложные routing-сценарии, включая pool strategy вроде `round_robin` и `least_loaded`.

#### UX-оценка
Это сильный шаг вперёд: SLA в панели используется не только как индикатор «горит/не горит», а как механизм, который влияет на порядок работы и эскалацию.

Хорошо и то, что orchestration вынесен на сервер — это правильнее, чем держать критичную логику только на фронте.

#### Ключевой риск
Зрелость логики повышает и риск конфигурационного долга. Чем больше правил и исключений, тем выше вероятность:
- конфликтующих маршрутов;
- непредсказуемых переназначений;
- ситуации, когда UX интерфейса кажется простым, а фактическое поведение определяется слишком сложной policy-моделью.

**Статус:** **реализовано сильно, но требует строгой governance-модели для правил**.

---

### 4. Контекст клиента — заметно сильнее, чем обычный «sidebar c фактами»

#### Что уже видно
Правая колонка workspace не ограничивается локальными полями тикета. По API и тестам видно, что система уже умеет:
- собирать client context;
- показывать profile health;
- проверять обязательные атрибуты профиля;
- работать с required sources;
- различать freshness/staleness источников;
- подключать enrichment профиля;
- использовать внешние источники и кэшировать их;
- деградировать в fallback/stale mode, а не просто падать.

Отдельно важен сам подход: context рассматривается как **проверяемый контракт**, а не как свободный набор полей.

#### UX-оценка
Это хороший вектор для enterprise-панели: оператору важен не просто «богатый sidebar», а **минимальный гарантированный контекст**, которому можно доверять.

#### Что пока не доказано полностью
Несмотря на зрелый каркас, в текущем репозитории нет достаточного основания утверждать, что CRM/contract enrichment уже закрыт end-to-end как боевая интеграция для всех сценариев. Скорее видно следующее:
- инфраструктура и API-контракт готовы;
- часть сценариев подтверждена тестами;
- production-grade полнота данных зависит от качества внешних источников и конкретных настроек.

То есть это уже не «идея», но ещё и не «вопрос окончательно закрыт».

**Статус:** **архитектурно зрелый foundation, operationally ещё требует доказательства качества данных**.

---

### 5. Макросы — уровень выше простого списка шаблонов

#### Что реально есть
В проекте реализованы:
- built-in переменные для макросов;
- catalog переменных из settings;
- внешний catalog переменных;
- ticket-context переменные;
- dry-run рендер макроса;
- default values;
- telemetry события для preview/apply;
- UI для поиска, выбора, preview и применения макросов в legacy и workspace сценариях.

#### UX-оценка
Это уже полезный инструмент ускорения операторской работы, а не декоративная функция. Особенно ценно наличие dry-run и контекстных переменных: это снижает риск ошибочного ответа и повышает предсказуемость результата.

#### Где нужно быть аккуратнее в формулировках
Текущая кодовая база даёт основания говорить о **governance visibility** и частичном governance instrumentation, но не о полностью завершённом жизненном цикле библиотеки макросов.

Иными словами:
- аудит и аналитические признаки есть;
- ownership/cleanup/review-процессы частично моделируются;
- но операционная дисциплина библиотеки ещё должна быть закреплена процессно, а не только через UI/API.

**Статус:** **реализовано существенно, до полной governance-модели ещё один шаг**.

---

### 6. Analytics и rollout governance — одна из самых интересных частей текущего состояния

#### Что реально существует
На analytics-экране и в telemetry-контуре уже есть:
- workspace rollout guardrails;
- агрегаты по событиям, ошибкам, fallback, slow open и KPI;
- parity/context gap breakdown;
- rollout scorecard;
- rollout governance packet;
- SLA routing governance audit;
- macro governance audit;
- external KPI gate и ссылки на внешние BI/dashboard зависимости.

Это сильный сигнал зрелости: команда уже проектирует не только экран, но и **процесс принятия решения, можно ли вообще раскатывать этот экран дальше**.

#### Почему это важно
Большинство внутренних панелей останавливаются на уровне «сделали фичу, посмотрели глазами, выкатываем». Здесь уже появляется другой подход:
- guardrails;
- scorecard;
- blocking items;
- owner sign-off;
- external dependency checks.

Это ближе к зрелой product operations модели.

#### Но есть важная оговорка
Наличие экрана и API ещё не гарантирует, что команда действительно живёт по этому process-loop каждую неделю. В коде видно, что система **умеет поддержать governance**, но эксплуатационная дисциплина всегда требует отдельного подтверждения.

**Статус:** **очень сильный foundation, но итоговая ценность зависит от регулярного использования процесса**.

---

## Что в предыдущем аудите было переоценено

Ниже — важная корректировка формулировок, чтобы документ не создавал ложного ощущения «всё уже production-complete».

### 1. «Workspace как основной режим уже закрыт» — пока рано формулировать так жёстко
Корректнее говорить:
- workspace уже технически силён;
- primary-flow поддержан флагами, API и аналитикой;
- но legacy fallback всё ещё часть штатной стратегии надёжности.

### 2. «Customer context закрыт» — пока скорее закрыт каркас, а не качество данных
Корректнее говорить:
- есть профильный контракт, source policy, freshness logic и enrichment path;
- но качество и полнота данных зависят от реальных интеграций и операционной поддержки источников.

### 3. «Macro governance реализован» — частично верно, но с поправкой
Корректнее говорить:
- библиотека макросов уже наблюдаема и частично управляемая;
- полноценный lifecycle management ещё должен стать процессом команды.

### 4. «Rollout decisioning уже зрелый» — да технически, не обязательно организационно
Корректнее говорить:
- платформа для decisioning есть;
- зрелость её применения определяется не только экраном, но и дисциплиной product/engineering review.

---

## Реально незавершённые темы

### Обновление от 24 марта 2026: что уже закрыто в коде

В этом цикле закрыт один из явно отмеченных разрывов между UX и эксплуатацией:

- **Triage preferences переведены из localStorage в серверный персистентный слой (per-operator).**
  Теперь представление (`view`), окно SLA, режим сортировки и размер страницы сохраняются на backend через `/api/dialogs/triage-preferences`, нормализуются и возвращаются в UTC-aware payload.
  При этом обратная совместимость сохранена: если серверная настройка отсутствует/пустая/невалидная, интерфейс продолжает работать на старом localStorage-поведении.
- **Добавлена telemetry-точка `triage_preferences_saved`** для контроля adoption нового persistence-контура.
- **Дата последнего обновления настроек (`updated_at_utc`) хранится и отдается в UTC**, с безопасной деградацией при пустых/битых значениях.

Это уменьшает один из ключевых рисков, отмеченных выше: triage больше не зависит только от клиентского состояния браузера и становится частью управляемого server-side контура.

### Обновление от 24 марта 2026 (вторая итерация): что закрыто дополнительно

Следующим по логике закрыт gap из P1 по **формализации legacy-only сценариев** в dual-run периоде:

- **Legacy-only inventory теперь редактируется прямо в analytics governance packet** (не только через настройки).
  Добавлен end-to-end контур сохранения списка legacy-only сценариев, review owner, review note и UTC timestamp через backend endpoint `/analytics/workspace-rollout/legacy-only-scenarios`.
- **В rollout packet добавлен структурированный блок `legacy_only_inventory`** с полями `reviewed_by`, `reviewed_at`, `review_note`, `review_timestamp_invalid`.
  Это делает инвентарь не просто списком строк, а управляемым артефактом review-процесса.
- **UTC/валидность дат обработаны безопасно**: пустые значения не ломают пакет, невалидная дата помечается как `invalid_utc`, попадает в `invalid_utc_items` и отображается в аналитике.
- **Добавлена telemetry-точка `workspace_legacy_inventory_updated`** для наблюдаемости adoption нового процесса.

Итог: переходный dual-run теперь поддерживается более дисциплинированно — список legacy-only сценариев не «живет в стороне», а становится частью регулярного governance-loop прямо в рабочем аналитическом контуре.

### Обновление от 24 марта 2026 (третья итерация): minimum customer context доведён до runtime-контракта

Следующим логичным шагом после формализации context standard стал runtime-контроль прямо в workspace:

- **В `workspace.v1` payload добавлен блок `context.contract`**, который проверяет minimum-profile контракт в реальном диалоге:
  mandatory fields, source-of-truth rules (`field:source`) и priority blocks.
- **Появилась явная оценка готовности контракта (`ready`) и список нарушений (`violations`)** с безопасной деградацией.
  Если настройки контракта не заданы, блок не блокирует поток (`enabled=false`, дефолтное поведение сохранено).
- **Во фронте workspace добавлен визуальный banner “Context contract”** в карточке клиента, чтобы оператор видел runtime-статус без перехода в settings/analytics.
- **Добавлена telemetry-точка `workspace_context_contract_gap`** и протянуты агрегаты в analytics summary + gap breakdown, чтобы adoption/проблемы контракта были наблюдаемы на уровне governance.

Итог: контур minimum customer context теперь не только описывается на уровне policy, но и проверяется end-to-end в рабочем потоке оператора (UI → API payload → telemetry → analytics breakdown).

### Обновление от 24 марта 2026 (четвёртая итерация): weekly rollout review стал измеряемым decision-loop

Следующий логичный gap из P1 (перевести rollout governance в регулярный ритуал) закрыт дополнительной инструментализацией review:

- **Подтверждение weekly review теперь пишет отдельные telemetry-события по decision action** (`go/hold/rollback`) и по факту привязки incident follow-up.
  Это позволяет видеть не только число подтверждений review, но и фактическое распределение решений.
- **В `rollout_packet.review_cadence` добавлены счётчики decision/follow-up за текущее UTC-окно**:
  `decision_go_events_in_window`, `decision_hold_events_in_window`, `decision_rollback_events_in_window`, `incident_followup_linked_events_in_window`.
- **Analytics UI расширен мета-строкой weekly review**: оператор и владелец rollout теперь видят распределение `go/hold/rollback` и число review с incident follow-up прямо в governance packet.
- **Обратная совместимость сохранена**: при отсутствии новых telemetry-событий счётчики остаются `0`, а существующий flow review не меняется.

Итог: weekly review перестаёт быть только бинарным чекбоксом «review подтверждён/не подтверждён» и становится наблюдаемым decision-loop, где можно отследить сдвиг решений и дисциплину incident follow-up в реальной эксплуатации.

### Обновление от 24 марта 2026 (пятая итерация): SLA policy governance получил отдельный review-checkpoint

Следующий логичный шаг из P2 по снижению конфигурационного долга в SLA-routing закрыт через отдельный governance-контур:

- **В analytics добавлена форма SLA policy governance review** (reviewed by, reviewed at UTC, decision go/hold, dry-run ticket id, review note) с сохранением через backend endpoint `/analytics/sla-policy/governance-review`.
- **В SLA routing governance audit добавлен структурированный блок `governance_review`**:
  `required`, `ready`, `reviewed_by`, `reviewed_at_utc`, `reviewed_at_invalid_utc`, `review_ttl_hours`, `review_age_hours`, `dry_run_ticket_required`, `dry_run_ticket_id`, `decision`, `review_note`.
- **Audit теперь умеет явно сигнализировать governance-gap по review слою**, включая:
  `governance_review_missing`, `governance_review_stale`, `governance_review_invalid_utc`, `governance_dry_run_ticket_missing`.
- **Добавлена telemetry-точка `workspace_sla_policy_review_updated` и счётчик в analytics summary**, чтобы было видно adoption review-практики за UTC-окно.
- **Обратная совместимость сохранена**: если новые governance-флаги не настроены, SLA routing audit работает как раньше и не блокирует существующий поток.

Итог: SLA policy governance перестаёт быть только «статичным dry-run отчётом» и становится управляемым review-loop с owner/date-дисциплиной, dry-run traceability и наблюдаемостью adoption.

### Обновление от 24 марта 2026 (шестая итерация): macro governance получил обязательный review-checkpoint

Следующий логичный шаг из P2 по предотвращению «macro entropy» закрыт через отдельный review-loop в analytics:

- **В analytics добавлена форма Macro governance review checkpoint** (reviewed by, reviewed at UTC, decision go/hold, cleanup ticket id, review note) с сохранением через backend endpoint `/analytics/macro-governance/review`.
- **В macro governance audit добавлен структурированный блок `governance_review`**:
  `required`, `ready`, `reviewed_by`, `reviewed_at_utc`, `reviewed_at_invalid_utc`, `review_ttl_hours`, `review_age_hours`, `cleanup_ticket_required`, `cleanup_ticket_id`, `decision`, `review_note`, `issues`.
- **Audit теперь явно сигнализирует review-gap по governance-слою**, включая:
  `governance_review_missing`, `governance_review_stale`, `governance_review_invalid_utc`, `governance_cleanup_ticket_missing`.
- **Добавлена telemetry-точка `workspace_macro_governance_review_updated` и счётчик в analytics summary**, чтобы было видно adoption review-практики в UTC-окне.
- **Обратная совместимость сохранена**: если новые флаги governance review не включены, macro audit работает как раньше, а отсутствие checkpoint не блокирует текущий поток.

Итог: macro governance перестаёт быть только «снимком проблем шаблонов» и становится управляемым operational-loop с owner/date-дисциплиной, cleanup traceability и измеряемым adoption.

### P1. Закрыть transition-state между workspace и legacy
### Обновление от 25 марта 2026 (седьмая итерация): legacy usage policy добавлен в rollout governance packet

Следующим шагом по снижению dual-run debt закрыт оставшийся gap из P1 про «legacy перестаёт быть ежедневным вариантом только по формальным критериям»:

- **В analytics добавлена форма Legacy manual-open policy review** (reviewed by, reviewed at UTC, max manual legacy share %, decision go/hold, review note) с сохранением через backend endpoint `/analytics/workspace-rollout/legacy-usage-policy`.
- **В rollout governance packet добавлен структурированный блок `legacy_usage_policy`**:
  `enabled`, `ready`, `reviewed_by`, `reviewed_at`, `review_note`, `review_ttl_hours`, `review_age_hours`, `review_timestamp_invalid`, `manual_legacy_open_events`, `workspace_open_events`, `manual_legacy_share_pct`, `max_manual_legacy_share_pct`, `threshold_ready`, `decision_required`, `decision`, `policy_updated_events_in_window`.
- **Добавлен отдельный packet item `legacy_usage_policy`**, который явно сигнализирует `hold`, если review просрочен, дата невалидна или доля manual legacy-open выше порога.
- **Добавлена telemetry-точка `workspace_legacy_usage_policy_updated`** и счётчик в analytics totals, чтобы adoption policy было видно в UTC-окне.
- **Обратная совместимость сохранена**: если policy-порог и флаги не заданы, новый checkpoint остаётся `off` и не меняет существующий rollout flow.

Итог: переход к primary workspace становится измеряемым не только через parity-gap/инвентарь edge-кейсов, но и через явный «бюджет» ручных legacy-open с owner/date-дисциплиной.

### Обновление от 25 марта 2026 (восьмая итерация): защита от breaking changes external macro catalog

Следующим шагом из P2 по снижению macro entropy закрыт пункт про защиту от несовместимых изменений во внешнем catalog:

- **В macro governance audit добавлен блок `external_catalog_contract`** с полями:
  `required`, `ready`, `expected_version`, `observed_version`, `verified_by`, `verified_at_utc`, `verified_at_invalid_utc`, `review_ttl_hours`, `review_age_hours`, `decision`, `review_note`, `issues`.
- **В analytics добавлена форма External catalog compatibility checkpoint** с сохранением через backend endpoint `/analytics/macro-governance/external-catalog-policy`.
- **Audit теперь явно сигнализирует breaking-risk**, включая:
  `external_catalog_expected_version_missing`, `external_catalog_observed_version_missing`, `external_catalog_version_mismatch`, `external_catalog_review_missing`, `external_catalog_review_invalid_utc`, `external_catalog_review_stale`.
- **Добавлена telemetry-точка `workspace_macro_external_catalog_policy_updated`**, чтобы adoption compatibility-checkpoint был наблюдаем в UTC-окне.
- **Обратная совместимость сохранена**: при выключенном флаге `macro_external_catalog_contract_required` новый checkpoint не влияет на текущий rollout flow.

Итог: внешний macro catalog перестаёт быть «неявной внешней зависимостью» и становится частью явного governance-контракта с version-pin и UTC-freshness review.

### Обновление от 25 марта 2026 (девятая итерация): deprecation policy макросов переведён в явный review-loop

Следующим логичным шагом из P2 по стабилизации lifecycle macro library закрыт оставшийся gap по deprecation discipline:

- **В macro governance audit добавлен блок `deprecation_policy`** с полями:
  `required`, `ready`, `reviewed_by`, `reviewed_at_utc`, `reviewed_at_invalid_utc`, `review_ttl_hours`, `review_age_hours`, `deprecation_ticket_required`, `deprecation_ticket_id`, `decision`, `review_note`, `issues`.
- **В analytics добавлена форма Deprecation policy checkpoint** с сохранением через backend endpoint `/analytics/macro-governance/deprecation-policy`.
- **Audit теперь явно сигнализирует deprecation governance-gap**, включая:
  `deprecation_policy_review_missing`, `deprecation_policy_review_invalid_utc`, `deprecation_policy_review_stale`, `deprecation_policy_ticket_missing`.
- **Добавлена telemetry-точка `workspace_macro_deprecation_policy_updated` и агрегат в analytics totals**, чтобы adoption review-практики по deprecation был наблюдаем в UTC-окне.
- **Обратная совместимость сохранена**: при выключенном флаге `macro_deprecation_policy_required` новый checkpoint не влияет на текущий rollout flow.

Итог: deprecation policy перестаёт быть только набором полей в шаблонах и становится управляемым operational-checkpoint с owner/date дисциплиной и traceability через ticket/decision.

### Обновление от 25 марта 2026 (десятая итерация): governance-настройки выведены в Settings UI

Следующим по логике закрыт практический UX-gap между backend-возможностями и операторским доступом к настройкам governance:

- **В Settings UI добавлены явные поля для rollout review discipline**:
  `decision_required`, `incident_followup_required`, `review_decision_action`, `review_incident_followup`.
- **В Settings UI добавлены поля baseline macro governance**:
  `require_owner`, `require_namespace`, `require_review`, `review_ttl_hours`, `deprecation_requires_reason`, `unused_days`.
- **Сохранение проходит end-to-end через существующий backend bridge `/settings`**:
  значения нормализуются, сохраняются в `dialog_config` и сразу попадают в analytics-аудиты (workspace rollout / macro governance) без дополнительных ручных шагов.
- **Обратная совместимость сохранена**:
  если новые поля пустые или выключены, используется прежний default-flow и поведение не меняется.

Итог: governance-настройки перестают быть «скрытыми ключами backend» и становятся операционно управляемыми прямо из стандартного UI настроек, что снижает риск рассинхрона между policy и фактической эксплуатацией.
### Обновление от 25 марта 2026 (одиннадцатая итерация): minimum profile стал сценарно-обязательным

Следующим логичным шагом из P1 по formalization minimum customer context закрыт пункт про **mandatory profile per scenario**:

- **В analytics-форме context governance добавлено поле `mandatory_fields_by_scenario` (JSON)** с сохранением через существующий endpoint `/analytics/workspace-context/standard`.
- **В runtime `workspace.v1` contract добавлены поля `active_scenarios`, `mandatory_fields_by_scenario`, `effective_mandatory_fields`** — обязательные атрибуты теперь вычисляются из общего baseline + профиля для активного сценария.
- **Сценарий определяется безопасно и UTC-neutral по данным диалога** (status/channel/categories): если match отсутствует, контракт откатывается к текущему global baseline без breaking-change.
- **Governance packet (`context_contract`) в analytics теперь учитывает наличие scenario-profile definitions** и показывает их в метрике готовности.
- **Обратная совместимость сохранена**: если новая map-настройка не задана, работает прежняя логика `mandatory_fields`.

Итог: minimum customer context перестаёт быть только «единым списком для всех кейсов» и становится управляемым сценарным контрактом без изменения legacy/default-потока.
### Обновление от 25 марта 2026 (двенадцатая итерация): SLA policy layering/ownership вынесены в Settings UI

Следующим по логике шагом из P2 закрыт практический разрыв между SLA governance-возможностями backend и операционным доступом через стандартный UI настроек:

- **В Settings UI добавлен блок `SLA policy governance baseline`** с полями:
  `require_layers`, `require_owner`, `require_review`, `review_ttl_hours`, `broad_rule_coverage_pct`, `block_on_conflicts`, `governance_review_required`, `governance_review_ttl_hours`, `governance_dry_run_ticket_required`.
- **Сохранение проходит end-to-end через `/settings`**:
  новые ключи мапятся в `dialog_config` и сразу влияют на SLA routing governance audit в аналитике без ручного редактирования JSON/конфига.
- **Обратная совместимость сохранена**:
  при пустых/выключенных полях используются прежние дефолты SLA-аудита, текущий flow не меняется.

Итог: policy layering, ownership discipline, conflict guard и governance review для SLA-routing теперь управляются из стандартного operational UI, а не остаются «скрытой» backend-конфигурацией.
### Обновление от 25 марта 2026 (тринадцатая итерация): scenario-aware source-of-truth и priority blocks для context contract

Следующим логичным шагом из P1 закрыт оставшийся разрыв по scenario-specific правилам в minimum customer context:

- **В analytics-форме context governance добавлены JSON-поля** `source_of_truth_by_scenario` и `priority_blocks_by_scenario` с end-to-end сохранением через `/analytics/workspace-context/standard`.
- **В runtime `workspace.v1` contract добавлены поля** `source_of_truth_by_scenario`, `priority_blocks_by_scenario`, `effective_source_of_truth`, `effective_priority_blocks`.
  Для активного сценария правила вычисляются как baseline + scenario overrides, при этом если новые map-настройки отсутствуют — сохраняется текущая baseline-логика.
- **В rollout governance packet (`context_contract`) добавлены scenario-aware структуры** и обновлён критерий `definition_ready`: теперь достаточно baseline или scenario-определений для mandatory/source/priority слоёв.
- **UTC/валидация и обратная совместимость сохранены**: пустые/невалидные значения не ломают сохранение и не меняют поведение без включения новых настроек.

Итог: minimum profile теперь формализован не только по mandatory fields, но и по source-of-truth/priority rules на уровне конкретных сценариев, что снижает риск ложных context-gap в runtime.
### Обновление от 26 марта 2026 (четырнадцатая итерация): manual legacy-open переведён под policy-контроль primary-flow

Следующим логичным шагом из P1 по снижению dual-run debt закрыт runtime-gap между analytics policy и ежедневным поведением оператора:

### Обновление от 26 марта 2026 (пятнадцатая итерация): manual legacy-open переведён под policy-контроль primary-flow

Следующим логичным шагом из P1 по снижению dual-run debt закрыт runtime-gap между analytics policy и ежедневным поведением оператора:

- **В `workspace` rollout meta добавлен блок `legacy_manual_open_policy`** с UTC-aware полями review (`reviewed_at_utc`, `review_age_hours`, `review_timestamp_invalid`), decision и block reason.
- **Во фронте workspace manual переход в legacy modal теперь проходит policy-checkpoint**:
при `blocked=true` переход останавливается, оператор получает явную причину block, а событие фиксируется в telemetry.
  при `blocked=true` переход останавливается, оператор получает явную причину block, а событие фиксируется в telemetry.
- **Добавлена telemetry-точка `workspace_open_legacy_blocked`** и отдельный аналитический счётчик blocked-attempts, чтобы видеть не только долю manual legacy-open, но и попытки обхода policy.
- **При включённой policy можно требовать explicit reason для manual legacy-open**; если политика не включена — поведение остаётся прежним (`manual_rollback` по умолчанию).
- **UTC/невалидные даты обработаны безопасно**: битый timestamp review не ломает workspace payload и превращается в явный `invalid_review_timestamp` reason.

Итог: legacy modal остаётся rollback-инструментом, но его ручное использование теперь связано с формальным policy-loop и наблюдаемостью на уровне telemetry/analytics.

Итог: legacy modal остаётся rollback-инструментом, но его ручное использование теперь связано с формальным policy-loop и наблюдаемостью на уровне telemetry/analytics.
Пока legacy modal остаётся быстрым rollback-сценарием, система фактически живёт в dual-run архитектуре. Это оправдано с точки зрения риска, но дорого с точки зрения простоты UX и сопровождения.

**Что нужно:**
- формально определить список legacy-only сценариев;
- регулярно смотреть parity-gap breakdown;
- зафиксировать условия, при которых legacy перестаёт быть ежедневным рабочим вариантом.

### P1. Довести customer context до обязательного рабочего стандарта
Сейчас видно, что механизм есть, но не до конца зафиксировано, **какой именно профиль обязан увидеть оператор** в конкретных типах кейсов.

**Что нужно:**
- mandatory profile per scenario;
- source-of-truth по каждому ключевому атрибуту;
- правила collapse/prioritization, чтобы правая колонка не превращалась в перегруженный реестр полей.

### P1. Перевести rollout governance из «возможности системы» в «ритуал команды»
Сейчас продукт хорошо инструментирован, но главный вопрос — насколько consistently команда опирается на это при решениях.

**Что нужно:**
- обязательный weekly review packet;
- единые `go / hold / rollback` критерии;
- явная связь incident follow-up ↔ next rollout decision.

### P2. Упростить сложность SLA policy
Система мощная, но мощность без слоёв владения быстро станет источником неожиданностей.

**Что нужно:**
- policy layering;
- ownership;
- dry-run review новых правил;
- аудит конфликтов и слишком широких маршрутов.

### P2. Не дать макросам и шаблонам расползтись в entropy
Макросы уже выглядят полезными, а значит со временем их станет слишком много.

**Что нужно:**
- owner на домен/namespace;
- usage-based cleanup;
- deprecation policy;
- защита от breaking changes во внешнем catalog.

---

## Приоритетный план действий

### Шаг 1. Зафиксировать workspace как управляемый primary-flow
Главная задача следующего цикла — не расширять UI-поверхность, а снизить стоимость dual-run режима.

### Шаг 2. Формализовать minimum customer context
Не добавлять бесконечные поля, а договориться о минимально обязательном и доверенном наборе данных.

### Шаг 3. Внедрить обязательный rollout packet в регулярную практику
Если scorecard, parity и KPI gate уже существуют, их нужно сделать обязательным основанием решения, а не опциональной справкой.

### Шаг 4. Навести порядок в SLA policy governance
Пока rules-слой не перегрузился, лучше заранее ввести ownership и review discipline.

### Шаг 5. Закрепить жизненный цикл macro library
Иначе производительность операторов вырастет краткосрочно, а операционный шум — долгосрочно.

---

## Основные риски
- **Dual-run debt:** пока workspace и legacy живут параллельно, продукт несёт двойную когнитивную и техническую нагрузку.
- **Policy complexity debt:** SLA-routing может стать слишком сложным для предсказуемого сопровождения.
- **Data trust gap:** богатый customer context не равен надёжному customer context.
- **Governance theater:** scorecard и audit-секции полезны только если по ним реально принимаются решения.
- **Template entropy:** макросы и variable catalogs без владельцев начинают ухудшать UX вместо его ускорения.

---

## Обновлённый вывод
Текущий репозиторий показывает, что Iguana уже ушла далеко вперёд от «панели со списком заявок». Наиболее зрелые зоны сейчас:
- triage-слой экрана диалогов;
- `workspace_v1` как почти основной операторский стол;
- серверная SLA orchestration;
- telemetry/analytics слой для rollout и quality control;
- развитая заготовка под customer context и macro productivity.

Но честная senior-level оценка звучит так:

> **Продукт уже богат на механизмы, но следующий выигрыш даст не расширение набора функций, а снижение переходной сложности и превращение instrumentation в регулярную операционную практику.**

Именно поэтому главный фокус следующего этапа должен быть не «что ещё добавить в интерфейс», а:
- сделать workspace окончательно управляемым primary-flow;
- договориться о минимальном обязательном customer context;
- дисциплинировать rollout и SLA-policy governance;
- не дать макросам и конфигурации превратиться в новый источник хаоса.
