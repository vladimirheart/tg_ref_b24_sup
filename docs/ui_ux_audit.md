# UI/UX аудит панели Iguana

**Дата актуализации:** 30 марта 2026 года.

## Executive summary
Панель перешла из стадии “собрать набор экранов” в стадию “удерживать управляемую операторскую систему”. Основные продуктовые риски P1 и P2 закрыты. Сильные стороны системы сейчас не в количестве фич, а в том, что ключевые потоки уже соединены end-to-end:

- `workspace_v1` работает как основной операторский контур, а не экспериментальный экран.
- rollout, SLA и macro governance считаются на сервере и поднимаются в analytics/workspace как operational signals.
- analytics поддерживает weekly review и decision-loop, а не только показывает raw counters.
- context contract и legacy closure-loop уже привязаны к telemetry, packet, UI и тестовому контракту.

Главный remaining-risk сместился: теперь нужно не изобретать новые governance-механизмы, а удерживать уже внедрённые контуры дешёвыми, понятными и устойчивыми в эксплуатации.

## Текущее состояние системы

### 1. Диалоги и triage
Состояние:
- список обращений уже поддерживает search, SLA-first сортировку, быстрые действия и bulk operations;
- triage-контур выглядит зрелым и пригодным для повседневной операторской работы;
- основной риск здесь уже не функциональный, а когнитивный: не перегрузить экран новыми управляющими сигналами.

Вывод:
- отдельного продуктового gap здесь нет;
- дальнейшие изменения должны быть направлены на упрощение чтения и сокращение визуального шума.

### 2. `workspace_v1`
Состояние:
- `workspace_v1` имеет свой shell, composer, drafts, macros, queue navigation и telemetry;
- controlled fallback в legacy сохраняется осознанно как safety-механизм rollout;
- runtime API и UI уже показывают operator-first summaries, а не только raw diagnostics.

Вывод:
- `workspace_v1` фактически стал primary operator flow;
- дальнейшая работа должна фокусироваться на удержании cheap-path и снижении secondary noise.

### 3. Context contract
Состояние:
- есть scenario-aware mandatory fields, source-of-truth, priority blocks и playbook coverage;
- runtime workspace показывает `operator_summary`, `next_step_summary`, `primary_violation_details` и progressive disclosure для вторичных нарушений;
- telemetry фиксирует раскрытие `sources`, `source/freshness policy` и `extra attributes`;
- analytics и rollout packet видят `secondary_noise`, `extra_attributes_compaction_candidate`, usage-level, top-section и management-review pressure;
- weekly review использует эти сигналы как часть общего `weekly_review_focus`.

Вывод:
- P1 по context contract закрыт;
- контур переведён в operational monitoring и tuning.

### 4. Legacy / dual-run discipline
Состояние:
- manual legacy-open policy, volume/trend gates и blocked reasons review уже есть;
- `legacy_only_inventory` показывает owner/deadline coverage, freshness, overdue, repeat-review, review queue, escalation и consolidation candidates;
- `review_queue` имеет next-action summary, management-review semantics и scenario-level visibility;
- эти сигналы поднимаются в packet, workspace/experiment summary, analytics и `p1_operational_control`.

Вывод:
- P1 по dual-run discipline закрыт;
- дальше это эксплуатационный closure-loop, а не незакрытая продуктовая capability.

### 5. SLA governance
Состояние:
- есть routing audit, review path, decision gate, lead-time visibility и churn-control;
- `minimum_required_review_path` и `cheap_review_path_confirmed` уже видны не только в audit, но и в summary API;
- `sla_review_path_control` и `p2_governance_control` показывают status/summary/next action;
- closure/freshness и churn вынесены в отдельные health-signals;
- weekly review видит не просто список checkpoint-ов, а cheap-path drift и типовые policy-change риски.

Вывод:
- шаг про “минимальный дешёвый SLA review path” закрыт;
- P2 по SLA governance закрыт и переведён в operational churn-control.

### 6. Macro governance
Состояние:
- macro governance включает ownership, cleanup, external catalog, deprecation и red-list сигналы;
- advisory noise разложен на actionable vs low-signal;
- analytics и summary API показывают `low_signal_backlog_dominant`, `actionable_advisory_share_pct`, `low_signal_advisory_share_pct`, closure/freshness и weekly priority;
- `p2_governance_control` объединяет macro noise с SLA churn в один управленческий сигнал.

Вывод:
- шаги по macro noise и governance freshness/closure закрыты;
- P2 по macro governance закрыт и переведён в operational noise-control.

### 7. Analytics и weekly review
Состояние:
- analytics уже работает как operational console для rollout, а не как статичный отчёт;
- mandatory-first presentation, compact summaries и aggregated `weekly_review_focus` уже внедрены;
- `weekly_review_focus` показывает focus health, top priority, priority mix, management-review pressure и next actions;
- P1/P2 operational controls уже сводятся в понятные summary-слои.

Вывод:
- weekly review можно использовать как реальный управленческий ритм;
- следующий риск не в нехватке данных, а в том, чтобы не превратить эти сигналы в alert fatigue.

## Что закрыто

### P1 закрыт
Закрыто:
- dual-run discipline;
- legacy review queue closure-loop;
- context contract как operator-first sidebar;
- hidden attributes / secondary noise monitoring;
- P1 operational control в summary API и фронте.

Текущее состояние:
- P1 больше не является открытым продуктовым блоком;
- это operational control с weekly monitoring.

### P2 закрыт
Закрыто:
- SLA governance cheap-path;
- SLA churn-control;
- macro governance noise-control;
- governance closure/freshness как управленческая норма;
- P2 operational control в summary API и фронте.

Текущее состояние:
- P2 больше не является открытым продуктовым блоком;
- это operational tuning по реальным telemetry-окнам.

### Шаг 1 закрыт
Смысл:
- удерживать закрытый P1 в режиме операционного контроля.

Статус:
- реализовано через `p1_operational_control`, `weekly_review_focus`, legacy queue pressure и context-noise signals.

### Шаг 2 закрыт
Смысл:
- зафиксировать минимальный дешёвый SLA review path.

Статус:
- реализовано через `minimum_required_review_path`, `cheap_review_path_confirmed`, `decision_lead_time_*` и `sla_review_path_control`.

### Шаг 3 закрыт
Смысл:
- перевести macro noise в usage-tier operational control.

Статус:
- реализовано через actionable vs low-signal split, backlog dominance и `p2_governance_control`.

### Шаг 4 закрыт
Смысл:
- сделать governance freshness/closure нормой weekly review.

Статус:
- реализовано через closure/freshness health для SLA и macro governance и их агрегацию в summary API.

## Что осталось

Открытых продуктовых задач по аудиту больше нет.

Остаётся только операционная норма:
- поддерживать тестовые фикстуры, telemetry и summaries в синхроне с runtime-кодом;
- сохранять UTC как обязательный формат для packet, analytics и integration fixtures;
- не расширять governance-поверхность без явного evidence-based запроса.

## Операционный режим

### Engineering guardrails
- integration и WebMvc-фикстуры должны идти через те же config-paths, что и runtime-код;
- новые telemetry tests должны использовать явные UTC timestamps и helper-based inserts;
- derived summaries нельзя добавлять только во фронт или только в analytics: они должны проходить через backend contract и тесты.

### Product guardrails
- `weekly_review_focus`, `p1_operational_control`, `sla_review_path_control` и `p2_governance_control` считаются достаточным управляющим слоем;
- новые checkpoint-ы допустимы только если существующие summaries не покрывают риск;
- compact, mandatory-first presentation остаётся обязательным UX-принципом.

## Итог
Система находится в хорошем состоянии для senior-level эксплуатации: основные архитектурные и продуктовые риски уже сняты, а дальнейшая ценность создаётся за счёт дисциплины, упрощения и удержания контуров в стабильном operational режиме.

Практический фокус сейчас такой:
- удерживать UTC и backward compatibility как обязательную норму;
- подтверждать через telemetry, что уже внедрённые policy-контуры остаются дешёвыми для команды;
- не открывать новый продуктовый долг там, где достаточно операционного контроля.
