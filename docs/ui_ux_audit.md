# UI/UX аудит панели Iguana

**Дата актуализации:** 27 марта 2026 года.

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
- runtime visibility в workspace и governance packet.

Оценка:
- это уже не просто sidebar, а управляемый контракт минимального контекста;
- remaining-gap находится в operator UX: причины нарушений должны быть ещё короче, понятнее и более action-oriented.

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
- blocked-attempt visibility и post-review blocked reasons.

Оценка:
- analytics уже поддерживает управленческий decision-loop;
- следующий вопрос не “добавить ещё gate”, а “какие из них реально снижают инциденты и не тормозят операторов”.

---

## Что остаётся незакрытым

### P1. Dual-run discipline ещё не доведена до sunset-фазы
Что уже закрыто:
- policy для manual legacy-open;
- volume/trend gates;
- blocked-trend visibility;
- обязательный review top blocked reasons.

Что остаётся:
- формальный sunset-процесс для `legacy-only` inventory;
- регулярное закрытие или консолидация сценариев, которые слишком долго живут только в legacy;
- явная дисциплина owner/deadline по overdue inventory.

### P1. Context contract ещё можно сделать понятнее для оператора
Что уже закрыто:
- scenario-aware rules;
- playbook links;
- короткие operator-friendly labels и next step в runtime workspace;
- runtime и analytics visibility.

Что остаётся:
- снизить визуальный шум в sidebar через приоритизацию и progressive disclosure.

### P2. SLA governance нужно удешевить
Что уже закрыто:
- layering/ownership/review checkpoints;
- decision gate;
- analytics review loop;
- risk-based review paths (`custom/light/standard/strict`);
- pre-review сигнализация конфликтующих routing rules;
- lead time от policy change до governance decision.

Что остаётся:
- упростить noisy governance-signals до 1-2 действительно обязательных путей эскалации;
- проверить, что обновление policy не создаёт лишний churn в review-cycle.

### P2. Macro governance нужно удержать в продуктивном режиме
Что уже закрыто:
- review checkpoint;
- external catalog checkpoint;
- deprecation checkpoint;
- baseline governance settings;
- red list для макросов с низким adoption;
- owner action для проблемных шаблонов;
- cleanup visibility по aliases/tags и неизвестным переменным.

Что остаётся:
- cleanup/deprecation SLA по usage-tier;
- проверить, что macro quality loop не даёт слишком много noisy red-list сигналов.

---

## Приоритетный план следующего этапа

### Шаг 1. Завершить sunset-дисциплину для legacy-only inventory
Цель:
- каждая legacy-only возможность либо закрывается, либо получает owner, deadline и явный follow-up.

Минимум на следующий цикл:
- hold-сигнал для overdue legacy-only inventory;
- явная метрика просроченных sunset commitments;
- удобный review сценариев, которые повторно остаются в legacy.

### Шаг 2. Довести context contract до action-oriented UX
Цель:
- оператор должен видеть не просто “что не так”, а “что сделать дальше”.

Минимум на следующий цикл:
- более аккуратная приоритизация блоков контекста в workspace.

### Шаг 3. Снизить стоимость SLA policy review
Цель:
- governance должен уменьшать риск, а не замедлять изменение правил.

Минимум на следующий цикл:
- отфильтровать noisy SLA governance checkpoints;
- определить минимальный обязательный review path для типовых policy change.

### Шаг 4. Ввести quality loop для macro catalog
Цель:
- чистка библиотеки должна идти по данным использования, а не по ручному ощущению.

Минимум на следующий цикл:
- usage-tier cleanup/deprecation SLA;
- фильтрация noisy macro governance сигналов.

### Шаг 5. Проверить реальную стоимость текущих policy-gates
Цель:
- понять, какие checkpoints действительно уменьшают инциденты, а какие только создают шум.

Минимум на следующий цикл:
- сравнить пользу gate-ов против времени review;
- отфильтровать noisy checkpoints;
- определить SLO для governance freshness и closure rate.

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
- довести dual-run до контролируемого sunset;
- сделать context contract максимально action-oriented;
- удержать SLA/macro governance в балансе между контролем и скоростью;
- измерять не число checkpoint-ов, а качество решений и их влияние на операторскую работу.
