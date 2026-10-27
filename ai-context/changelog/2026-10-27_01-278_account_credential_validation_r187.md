# 01-278 — логины, пароли и точный ключ активности, R187

## Пользовательский запрос

Промты пользователя:

Исходный выбор: «выбери один пункт и продолжай».

Уточнение пользователя:
> вот идёт работа по "если в логине есть пробел". может проще убирать пробел до и после логина? ну и добавить тултип и подсказку если не верные символы присутствуют в логине-пароле при создании\зменении

На вопрос о допустимых символах новых/явно изменяемых логинов:
> Только латиница, цифры и . _ - @; без пробелов внутри

Продолжение: «продолжи». Ранее пользователь разрешил автоматические commit/push
до конца текущих суток; срок — 2026-10-27 21:00 UTC, 28 октября 00:00 Moscow.
Рабочий проект и Docker находятся на другой машине и в другой сессии.

## Изменение

- Создание и явное переименование логина удаляют обычные крайние пробелы,
  затем проверяют ASCII allowlist на клиенте и сервере. Сервер отклоняет
  коллизию с другим legacy логином после такой обрезки и case folding.
  TRIM здесь только проверяет конфликт новой записи, не выбирает аккаунт.
- Неизменённый legacy username не отправляется при сохранении профиля;
  неизменённое значение в API тоже сохраняется без переименования.
- Пароли и подтверждения передаются точно, без trim, включая смену через
  меню пользователя. Общая серверная политика запрещает blank, controls,
  malformed Unicode и превышение 72 байт UTF-8 существующего bcrypt encoder.
  Текущий credential не подвергается новой политике длины. Права сохранены.
- У полей логина/паролей появились видимые подсказки, title tooltips,
  accessible inline errors и точная проверка подтверждения. Вставка controls
  блокируется до удаления CR/LF браузером; ошибки/уведомления сбрасываются
  при следующем открытии формы. Пароли не отражаются в подсказках.
- UserLastActivityFilter больше не обрезает authenticated username перед SQL.
  Исходно выбранный дефект закрыт без изменения старых account keys.
- Обновлены cache versions auth-management.js/sidebar.js. В существующем
  SidebarRuntimeUiSourceContractTest обновлено только точное ожидаемое значение
  tag; проверка однократной инициализации и остальные assertions сохранены.
- Добавлены четыре Java boundary suites, две real DOM browser QA и постоянное
  правило 08-panel-account-credentials; обновлены task detail и S0.

## Проверки

Maven wrapper: 13 suites / 255 tests, failures/errors/skips=0 (v9).
129 новых Java cases: frozen RED до соответствующих production edits —
75 failures / 54 passing controls / 0 errors; те же test bytes GREEN.
MVC/mocked JDBC проверяют payload, SQL binds, отказ до hash/write, legacy
профиль, raw passwords, 72 UTF-8 bytes, права и best-effort activity filter.
Существующие security/Thymeleaf suites также GREEN. Это не проверка реальной БД.

Headless Edge: 61 account-form + 15 sidebar-password cases GREEN, источник —
настоящие fragments и JS, fixture-only fetch, вся сеть заблокирована.
Frozen RED: 53 failures / 23 controls / 0 errors. Проверены edge spaces,
ASCII/Unicode, exact confirmation, paste, ошибки/retry/reset, legacy usernames,
tooltips/aria и права формы. Финальный account-form run — v2 после сброса
уведомления об обрезке; profile-password run — v1.

Node: 40 scripts / 110 checks GREEN, skips/cancelled=0. Полный 41-script run:
111 checks, один прежний R53 notes fixture failure; отдельно воспроизведён на
неизменённых normalized inputs baseline b92c1aa26. Полный Node suite не GREEN.

Подготовительные fixture errors не считаются RED доказательством: ранний
collision fixture требовал отсутствующий H2 driver; до production edits заменён
на mock SQL contract без новой зависимости. В новом sidebar browser fixture
исправлены bootstrap syntax и about:blank storage (memory storage); meaningful
RED зафиксирован после исправления harness. В v8 единственное падение — старый
cache tag в source contract; в v9 точное ожидание актуализировано.

## Среда и оставшаяся работа

Baseline: clean main, HEAD/origin/main/actual remote
9b7bd6f6bd8f8f30a41bd2ecae7910133bab214a. R186 сверён по parent/subject/exact
9 files/normalized content. Detached sandbox .tmp/01-278-r187; source checkout
C:\Users\sushi\Git_H\tg_ref_b24_sup. Java cache R184 использован только после
сверки всех baseline Java sources; изменённые/new Java скомпилированы wrapper.
Source apply и expiry-guarded Git publisher разделены; фактические состояния,
exact files/content и test/report hashes фиксируются в ignored R187 artifacts.
Generated CSS имеет тот же Git blob, в scope не включается. POM/schema не менялись.

Рабочий экземпляр: другая машина/сессия C:\Users\SinicinVV\git_h\tg_ref_b24_sup.
Checkpoint panel-web R184 и пользовательский start/stop одного бота сохранены
точно. Здесь нет Docker/live DB/HTTP/provider probes, рабочего restart/deploy,
migration/backfill/config/flags или массового исправления логинов. R187 здесь
не развёрнут. 01-278 остаётся 🟡: другие legacy identity consumers, global/system
policy, S2B, сквозная S3–S5 изоляция и worker/fleet/rollout S6 ещё открыты.
