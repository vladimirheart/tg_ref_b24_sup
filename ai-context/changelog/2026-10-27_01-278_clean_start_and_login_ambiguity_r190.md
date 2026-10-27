# 01-278 R190 — чистый первый запуск и отказ при неоднозначном логине

Дата: 2026-10-27. Baseline: a4d263eb389aa715551c8ff57ea4f3958fb29e83.

Промт пользователя:

> тогда продолжай работу. старые данные в корне не интересны - проект ещё не запускался в прод, все данные являются тестами не имеющими ценности

## Изменение

- Task/S0/бывшая форма S2B фиксируют: production ещё не запускался, весь dataset
  тестовый. Historical alias mapping, record classification, dry-run/backfill и
  сохранение старых counts/ID/связей сняты с обязательного плана. Первый запуск
  планируется с чистой базой. Future manual/global task, project и system policy
  остаются отдельными решениями; security/owner/child guards обязательны.
- UserRepositoryUserDetailsService принимает только ровно одну account row
  по прежнему raw login и SQL case folding, читая LIMIT 2 вместо LIMIT 1.
  Коллизии/отсутствие дают общий отказ до чтения прав. Точный регистр и порядок
  строк не выбирают аккаунт. Remember-me получает тот же guard; открытая
  numeric-ID сессия R189 остаётся своим аккаунтом. Schema/Flyway не изменены.
- Добавлен AmbiguousLoginWebMvcTest; три существующие JDBC fixtures адаптированы
  к queryForList/LIMIT 2. Assertions сохранены: преобразования воспроизводятся
  из baseline по exact manifest, без ослабления security expectations.
- Обновлено правило 08-panel-account-credentials. R184 runtime checkpoint и
  пользовательский start/stop одного бота сохранены точно. Новая оговорка перед
  checkpoint объясняет: историческое production wording относится к тестовому
  стенду. Старые changelog-файлы не изменены. Общая задача остаётся 🟡.

## Валидация

Новые frozen 18 MockMvc cases на baseline: 9 failures / 9 controls / 0 errors;
те же bytes после fix GREEN. Проверены case/order/duplicate/multiple matches,
unique raw keys, generic failure, remember-me после новой коллизии, собственная
ID-bound сессия и store failure. Final Maven v2: 14 suites / 600 tests GREEN,
failures/errors/skips=0; прежние 582 cases сохранены. Настоящие SecurityConfig/
DAO/encoder/login/session/CSRF/remember-me и /whoami, mocked только JDBC.

R189 проверен по parent/subject/exact 10 files/content; кеш Java classes
использован только после совпадения всех baseline Java sources. Изменённые
source/class/report hashes и freshness, exact scope, transform replay,
checkpoint и CSS no-op blobs проверяются в ignored .tmp/r190-* artifacts.
Evidence: .tmp/r190-panel-v1.log (baseline RED), .tmp/r190-panel-v2.log (GREEN),
.tmp/r190-baseline-red.json, .tmp/r190-transform.json, .tmp/r190-tests-green.json.
Browser/Node/Docker/live DB/HTTP/providers не запускались; PostgreSQL collation
и рабочий JDBC session store здесь не проверены.

## Среда и границы

Source/test checkout: C:\Users\sushi\Git_H\tg_ref_b24_sup; detached sandbox
.tmp/01-278-r190. Рабочие проект/Docker тестового стенда — на другой машине/
в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Очистка/пересоздание
БД или файлов данных, migrations/backfill, flags/config, runtime probes/restart/
deploy, session-store очистка и provider sends здесь не выполнялись.
R185–R190 здесь не развёрнуты; R189 session/image compatibility остаётся
условием будущего обновления. Source apply отделён от publisher с проверкой
grant до 2026-10-27 21:00 UTC; commit/push фиксируются после реальных действий
в ignored .tmp/r190-result.json. S3–S5, fleet acceptance и первый запуск S6
не объявляются завершёнными.
