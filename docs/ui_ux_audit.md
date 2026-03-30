-# UI/UX аудит панели Iguana

**Дата актуализации:** 30 марта 2026 года.

## Зачем обновлён документ
Предыдущая версия накопила слишком много итерационной истории и перестала быть удобным рабочим артефактом. Эта версия:
- фиксирует только актуальное состояние;
- убирает длинную хронологию изменений;
- оставляет краткий, реалистичный план следующего этапа.

## Executive summary
Панель уже представляет собой не набор разрозненных экранов, а управляемую операторскую систему с четырьмя сильными слоями:

1. **Triage и очередь диалогов**  
   Сильный список обращений с фильтрами, SLA-first сценариями, bulk actions и быстрыми действиями.

2. **`workspace_v1` как основной рабочий контур**  
   Есть отдельный shell, composer, контекст клиента, navigation, telemetry, policy-checkpoints и controlled fallback в legacy.

3. **Server-side orchestration и governance**  
   SLA routing, auto-assign, webhook escalation, rollout scorecard, governance packet и policy-аудиты уже считаются на сервере, а не только во фронте.

4. **Analytics как decision-loop, а не просто отчёт**  
   В системе есть guardrails, breakdown по gap-ам, KPI gate, governance review checkpoints и operational visibility для rollout.

Главный вывод: **фундамент платформы собран хорошо**. Основной риск сейчас не в нехватке механизмов, а в том, чтобы сделать уже существующие policy-контуры дешёвыми, понятными и обязательными в повседневной эксплуатации.

---

## Что уже выглядит зрелым

### 1. Диалоги и triage
Реализованы:
- быстрый поиск;
- сохранённые рабочие режимы списка;
- SLA-first сортировка и risk-oriented views;
- bulk actions;
- быстрые действия без глубокого перехода в карточку.

Оценка:
- сильный операторский экран;
- хорошо закрывает поиск, приоритизацию и массовые действия;
- следующий риск здесь не функциональный, а UX-когнитивный: интерфейс уже плотный по возможностям.

### 2. `workspace_v1`
Реализованы:
- отдельный рабочий shell;
- composer, drafts, macros, reply target;
- навигация по очереди;
- отдельные блоки клиентского контекста;
- баннеры rollout/parity/readonly;
- telemetry и manual legacy-open policy.

Оценка:
- это уже основной кандидат на primary-flow;
- dual-run модель всё ещё сохраняется осознанно, как механизм безопасного rollout;
- технический долг сместился из области “построить workspace” в область “сократить причины возврата в legacy”.

### 3. SLA orchestration и policy-routing
Реализованы:
- SLA thresholds и server-side signals;
- escalation logic;
- webhook fan-out с retry/cooldown;
- auto-assignment и routing rules;
- governance-аудит для SLA policy.

Оценка:
- архитектурно слой выглядит сильным;
- основной риск теперь в конфигурационной сложности и стоимости review, а не в отсутствии механизмов.

### 4. Customer context contract
Реализованы:
- required attributes;
- required sources;
- freshness/staleness;
- scenario-aware mandatory/source/priority rules;
- playbook links;
- runtime visibility в workspace и governance packet;
- operator-friendly summary / next-step для runtime workspace;
- progressive disclosure для вторичных violation-details.

Оценка:
- это уже не просто sidebar, а управляемый контракт минимального контекста;
- основной remaining-gap сместился из “сделать понятно” в “не перегрузить оператора вторичными источниками и policy-деталями”.

### 5. Macro system и macro governance
Реализованы:
- preview/apply telemetry;
- variable catalog;
- external catalog compatibility checkpoint;
- governance review;
- deprecation policy;
- cleanup visibility.

Оценка:
- макросы уже являются рабочим ускорителем, а не декоративной функцией;
- основной риск следующего этапа: не превратить governance в бюрократию.

### 6. Rollout governance и analytics
Реализованы:
- guardrails;
- scorecard;
- governance packet;
- weekly review discipline;
- criteria/follow-up gates;
- legacy usage policy;
- blocked-attempt visibility и post-review blocked reasons;
- sunset coverage / overdue / repeat-review visibility для legacy-only inventory;
- closure/freshness metrics для SLA и macro governance;
- mandatory-first сортировка governance-сигналов в analytics;
- aggregated `weekly review focus`, который сводит legacy/context/SLA/macro follow-up в единый action-loop и сразу показывает top priority / next action / management-review pressure.
- `weekly review focus` теперь также показывает focus health, priority mix и количество секций, которые уже требуют management review, а не просто общий список follow-up.

Оценка:
- analytics уже поддерживает управленческий decision-loop;
- главный вопрос теперь не “добавить ещё gate”, а “удержать noise под контролем и не вернуть команду в alert fatigue”.

---

## Что остаётся незакрытым

### P1. Dual-run discipline: sunset-процесс уже формализован, но нужен closure-loop
Что уже закрыто:
- policy для manual legacy-open;
- volume/trend gates;
- blocked-trend visibility;
- обязательный review top blocked reasons;
- owner/deadline coverage для `legacy-only` inventory;
- overdue sunset commitments;
- repeat legacy review;
- review-queue для сценариев, которые повторно остаются в legacy;
- follow-up summary / oldest due / repeat-cycle visibility для `review_queue`.

Что остаётся:
- регулярное закрытие или консолидация долгоживущих legacy-only сценариев;
- дисциплина еженедельного follow-up по сценариям, которые несколько циклов подряд остаются в review-queue.

### P1. Context contract: action-oriented UX уже собран, но нужен ещё более дешёвый sidebar
Что уже закрыто:
- scenario-aware rules;
- playbook links;
- короткие operator-friendly labels и next step в runtime workspace;
- runtime и analytics visibility;
- progressive disclosure для вторичных context violations;
- secondary `sources` и `source/freshness policy` блоки свернуты в дешёвый details-first режим с компактным summary.
- secondary `sources` / `source-freshness policy` / `extra attributes` теперь дают disclosure telemetry, так что sidebar можно оценивать по фактическому раскрытию, а не по ощущениям.
- analytics summary теперь показывает usage-level и top opened secondary section, а не только сырой счётчик раскрытий.
- `extra attributes` получили отдельный telemetry-compaction сигнал, чтобы отличать общий secondary-context noise от точечной перегрузки именно hidden attributes.

Что остаётся:
- подтвердить по первым usage windows, что heavy раскрытие secondary context не становится новой нормой для операторов;
- при необходимости ещё сильнее ужать `extra attributes`, если они продолжают конкурировать с primary context.

### P2. SLA governance: ядро удешевлено, дальше нужен churn-control
Что уже закрыто:
- layering/ownership/review checkpoints;
- decision gate;
- analytics review loop;
- risk-based review paths (`custom/light/standard/strict`);
- pre-review сигнализация конфликтующих routing rules;
- lead time от policy change до governance decision;
- minimum required review path;
- closure/freshness metrics;
- mandatory-first presentation в analytics;
- weekly review priority и churn-risk summary для дешёвого weekly governance review.
- telemetry summary теперь показывает `policy churn` против decision-цикла, а не только статический governance audit.
- analytics теперь показывает churn-level и follow-up pressure, а не просто процент churn.

Что остаётся:
- проверить, что реальный `policy churn` не остаётся высоким на типовых policy changes;
- при необходимости сократить advisory checkpoint-ы ещё на один уровень для типовых policy changes.

### P2. Macro governance: quality loop уже на данных, но noise всё ещё под наблюдением
Что уже закрыто:
- review checkpoint;
- external catalog checkpoint;
- deprecation checkpoint;
- baseline governance settings;
- red list для макросов с низким adoption;
- owner action для проблемных шаблонов;
- cleanup visibility по aliases/tags и неизвестным переменным;
- usage-tier cleanup SLA;
- usage-tier deprecation SLA;
- closure/freshness metrics;
- mandatory-first analytics, noise-level summary и weekly review priority.
- advisory noise теперь разложен на actionable vs low-signal red-list кандидаты, чтобы не бюрократизировать легитимно низкое использование.

Что остаётся:
- проверить, что actionable advisory остаётся главным сигналом, а low-signal red-list не превращается в ручной backlog по инерции;
- удержать policy в минимальном обязательном контуре без возврата в бюрократию.

### P2. Документ и тестовые фикстуры нужно держать ближе к коду
Что уже закрыто:
- аудит снова стал рабочим артефактом;
- основные rollout/context/governance доработки описаны на уровне состояния, а не истории.
- focused WebMvc-покрытие теперь отдельно проверяет и насыщенный `weekly_review_focus`, и пустой `ok`-сценарий без follow-up.

Что остаётся:
- периодически чистить WebMvc/integration fixtures, чтобы они не отставали от реальных конструкторов и workspace-контрактов.

---

## Приоритетный план следующего этапа

### Шаг 1. Закрыть оставшиеся долгоживущие legacy-only сценарии
Цель:
- перевести sunset-дисциплину из режима “видим долг” в режим “системно закрываем”.

Минимум на следующий цикл:
- weekly closure review для `review_queue_scenarios` по новым `review_queue_summary` / `repeat_cycles`;
- отдельный follow-up для сценариев, которые остаются в очереди несколько циклов подряд.

### Шаг 2. Ещё сильнее ужать runtime context noise
Цель:
- оператор должен видеть customer-context priority до любых вторичных policy деталей.

Минимум на следующий цикл:
- посмотреть disclosure telemetry по вторичным `sources` / `source-freshness policy` / `extra attributes`;
- проверить фактическую частоту heavy раскрытия hidden details в workspace.

### Шаг 3. Зафиксировать минимальный дешёвый SLA review path
Цель:
- governance должен уменьшать риск, а не замедлять изменение правил.

Минимум на следующий цикл:
- подтвердить, что `minimum_required_review_path` покрывает типовые policy changes;
- измерить policy churn против decision lead time на реальных изменениях правил.

### Шаг 4. Проверить macro noise на реальных usage-tier сценариях
Цель:
- cleanup библиотеки должен идти по данным использования, а не по ручному ощущению.

Минимум на следующий цикл:
- сравнить advisory noise против обязательных macro checkpoint-ов;
- при необходимости понизить часть low-signal red-list сигналов до чисто аналитических.

### Шаг 5. Сделать governance freshness/closure управленческой нормой
Цель:
- оценивать не количество checkpoint-ов, а качество и своевременность их закрытия.

Минимум на следующий цикл:
- использовать closure/freshness rate как стандартную weekly review метрику;
- отследить, где noise-level остаётся высоким несмотря на хорошие closure numbers.

---

## Основные риски следующего этапа
- **Governance overload**: слишком много checkpoint-ов могут ухудшить скорость команды.
- **Shadow bypass**: часть процессов может снова уйти в ручные обходы.
- **Metric gaming**: прохождение gate-ов может стать важнее реального UX-outcome.
- **Alert fatigue**: избыток сигналов без приоритизации снизит ценность analytics.
- **Ownership drift**: без устойчивых владельцев policy-контуры начнут деградировать.

---

## Обновлённый вывод
Система уже перешла из стадии “собрать фичи” в стадию “сделать эксплуатацию предсказуемой”. Senior-level фокус на текущий момент такой:

> **Следующий этап — не наращивать количество governance-механизмов, а упростить ежедневную работу с уже существующими правилами и ускорить закрытие явных operational-долгов.**

Практический приоритет:
- закрыть долгоживущие legacy-only сценарии, а не только считать их;
- удержать context UX в режиме “сначала главное, детали по запросу” и проверять реальное раскрытие вторичных блоков;
- держать SLA/macro governance в балансе между контролем и скоростью, используя weekly priority вместо добавления новых чекпоинтов;
- измерять не число checkpoint-ов, а closure/freshness, noise-level и влияние на ежедневную операторскую работу.
