# 01-278 R191 — смена собственного пароля по numeric identity

Дата: 2026-10-27. Baseline: 96878bd4bc04eb5126b9a2db36e1dba3e1ff8b34.

Промт пользователя:

> делай

Контекст: продолжить проверки доступа/изоляции новых данных после R190;
проект ещё не запускался в production, старый dataset тестовый и не переносится.

## Изменение

- ProfileApiController больше не ищет собственный аккаунт через lower(username)
  и не обновляет все case-fold matches. Нужен authenticated PanelUserPrincipal
  с positive numeric ID; чтение по ID сверяет exact stored username/status.
- Сохранение — один ID/username/original-credential/status CAS UPDATE.
  Null-safe predicates включают password и все присутствующие password_hash/
  enabled/is_blocked columns. Concurrent hash/password, rename, delete или
  блокировка/отключение не перезаписываются; conflict/неожиданный count — 409.
  Свой current password остаётся обязательным. Чужие аккаунты не затрагиваются.
- Optional password_hash определяется из SELECT * snapshot, без metadata fallback;
  оба hash поля пишутся вместе при наличии колонки. Raw input/confirmation и
  CredentialPasswordPolicy не изменены. Ui preferences/admin management вне scope.
- PanelUserPrincipal и его ID/constructor открыты typed controller/test consumers;
  сериализуемые поля/UID прежние. Добавлен ProfilePasswordIdentityWebMvcTest,
  два прежних profile fixtures переведены на numeric principal/ID/CAS expectations.
- Task/S0/credentials rule обновлены; historical S2B снят решением R190.
  R184 runtime checkpoint и ручной start/stop одного бота сохранены точно;
  все упоминания production в старых checkpoint — история тестового стенда.
  Старые changelog-файлы не изменены; общий task status — 🟡.

## Валидация

Новый harness v1 выявил ошибку настройки: csrf() заменял token repository,
из-за чего отсутствовала native cookie. Переведён на real login form cookie/
masked token. V2 выявил неполную metadata stub для uppercase PASSWORD_HASH
при отсутствии колонки; stub исправлена. Production оставался baseline;
v1/v2 errors не использованы как доказательство security RED. Артефакты сохранены.

Meaningful baseline v3: frozen 35 cases / 18 failures / 17 controls / 0 errors;
те же test bytes после fix GREEN. Final Maven v4: 15 suites / 635 tests,
failures/errors/skips=0. Real SecurityConfig/login/session/CSRF/encoder,
stateful mocked JDBC и три direct-controller guard cases. Проверены unique/raw
logins, hash-column present/absent и NULL, обе очередности case collisions,
foreign current password, corrupt/missing identity, read/write races, legacy/
unauthenticated principals, validation/CSRF/anonymous controls. Прежние 600 cases
сохранены, baseline transformations replay точно подтверждает fixture changes.

R190 сверён по parent/subject/exact 10 files/content. Кеш classes используется
после совпадения всех baseline Java sources. Exact source/class/report freshness,
content/evidence SHA, protected checkpoint и generated CSS no-op blobs проверены
в ignored .tmp/r191-* artifacts. Evidence: .tmp/r191-panel-v3.log (baseline RED),
.tmp/r191-panel-v4.log (GREEN), .tmp/r191-baseline-red.json,
.tmp/r191-transform.json, .tmp/r191-tests-green.json.
Это не live PostgreSQL locking/driver или рабочий JDBC session store.
Browser/Node/Docker/live DB/HTTP/providers здесь не запускались.

## Среда и границы

Source/test checkout: C:\Users\sushi\Git_H\tg_ref_b24_sup; detached sandbox
.tmp/01-278-r191. Рабочие проект/Docker тестового стенда — на другой машине/
в другой сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Нет runtime probes/
restart/deploy, schema/data reset, migrations/backfill, flags/config, provider
sends или session-store cleanup. R185–R191 здесь не развёрнуты; R189 session/image
compatibility остаётся условием будущего обновления. Общая revocation/token
policy не меняется. Source apply отделён от publisher с expiry guard
2026-10-27 21:00 UTC; commit/push фиксируются после фактических действий
в ignored .tmp/r191-result.json. S3–S5/fleet acceptance/первый запуск S6 не закрыты.
