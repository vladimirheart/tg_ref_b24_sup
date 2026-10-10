# 01-278 — входные данные для согласования S2B

## Статус

🟡 Ожидается решение владельцев бизнеса и продукта. Это форма для
согласования, а не разрешение на migration, backfill, rollout или включение
feature flag.

Файл намеренно не содержит фактических legacy aliases, идентификаторов
обращений и персональных данных. Такие значения передаются только через
защищённый согласованный контур, а здесь указывается ссылка на запись и её
проверяемый fingerprint.

## Что нужно согласовать

### 1. Точное сопоставление legacy alias

Для каждого из трёх непустых значений `messages.business` в защищённой записи
нужно зафиксировать одно из решений ниже. Нельзя выводить принадлежность из
названия канала, регистра, пробелов, последнего выбора оператора или похожего
написания.

| Ссылка на защищённую запись | Fingerprint exact UTF-8 значения | Решение | Целевой `business.code` | Approver | Evidence | Дата |
| --- | --- | --- | --- | --- | --- | --- |
| `<private-record-ref>` | `<sha256-or-approved-fingerprint>` | `MAP` / `KEEP_UNRESOLVED` | `<existing-code-or-n/a>` | `<name-or-role>` | `<source>` | `<YYYY-MM-DD>` |

Для `MAP` требуется ровно один существующий active business. Actual value
alias и его производные не должны попадать в source, changelog, логи или
тестовые fixtures. S2B сможет сравнивать значение лишь побайтно, как S2A
preview; нормализация, эвристика и неявный fallback запрещены.

### 2. Три обращения без legacy business label

Для каждого обращения с пустым `messages.business` нужен отдельный outcome.

| Ссылка на защищённый locator обращения | Решение | `business.code`, если `ASSIGN` | Evidence | Approver | Дата |
| --- | --- | --- | --- | --- | --- |
| `<private-ticket-reference>` | `ASSIGN` / `KEEP_UNRESOLVED` | `<existing-code-or-n/a>` | `<source>` | `<name-or-role>` | `<YYYY-MM-DD>` |

`KEEP_UNRESOLVED` — допустимый и безопасный outcome; он не даёт доступ к
обращению через чужой business scope. Автоматически назначать бизнес таким
строкам нельзя.

### 3. Расхождения исторического канала

Для трёх расхождений `chat_history.channel_id` требуется классификация для
каждого canonical ticket.

| Ссылка на защищённый locator обращения | Класс | Источник отображаемой принадлежности | Evidence | Approver |
| --- | --- | --- | --- | --- |
| `<private-ticket-reference>` | `LEGAL_TRANSFER` / `BAD_REFERENCE` / `UNRESOLVED` | `CANONICAL_TICKET` | `<source>` | `<name-or-role>` |

Business owner определяется только canonical ticket. Историческая строка не
может расширить scope читателя.

### 4. Shared channel и операции с обращением

Product owner должен утвердить оба решения:

| Область | Обязательное решение |
| --- | --- |
| Shared channel | приоритет разрешения: explicit ticket owner, explicit channel/location mapping или `UNRESOLVED`; UX при конфликте и при отсутствии mapping |
| Ticket actions | матрица action → минимальная capability (`READ`, `OPERATE`, `MANAGE`, `CONFIGURE`), включая создание, ответ, назначение, закрытие, удаление и экспорт |
| Tasks / projects / boards | policy для `business`, `global_system`, `unresolved`; владелец и фильтрация каждой глобальной сущности |
| Incidents / notifications | отдельный тип системного события, получатели и полномочие на просмотр; delivery channel не расширяет data scope |

Если строка матрицы не утверждена, соответствующий S2B/S3 path остаётся
выключенным и deny-by-default.

## Критерии принятия входных данных

- Каждое решение имеет approver, дату и проверяемое evidence.
- Все `business.code` существуют в утверждённом справочнике и активны.
- В protected decision record сохранены exact values и fingerprint; в
  репозитории остаются только ссылки и безопасная классификация.
- Предварительный read-only preview даёт ожидаемые counts
  `READY_TO_ASSIGN` / `ALREADY_ASSIGNED` / `UNRESOLVED` /
  `AMBIGUOUS_MAPPING` / `CONFLICT`; любой неожиданный результат возвращается
  на ручное решение.
- До отдельного разрешения на controlled write отсутствуют записи в
  `tickets.business_id`, изменение legacy полей, включение feature flags и
  запуск migration/backfill в рабочем контуре.

## Граница следующего шага

Подтверждённый документ снимает только блокировку проектирования S2B. Для
реализации controlled write, dry-run или backfill потребуется отдельный
явный scope с критериями preflight, rollback и acceptance; настоящее
согласование его не заменяет.
