# Поиск связанного объекта и внешний справочник — R19

26.10.2026, задача01-281, 🟣. Код применён, panel-web обновлён, V82 выполнена.
Commit/push не выполнялись.

## Как пользоваться

- «Инциденты → Новый инцидент»: выберите тип, начните вводить номер или название
  в «Номер связанного объекта» и выберите найденную запись. Свободный текст не
  считается выбором. Доступны диалог, задача, паспорт и внешний объект.
- «Инциденты → Внешние объекты»: общий справочник портала. Глобальный администратор
  может добавить название, внешний номер, http(s)-ссылку и описание. Остальные
  пользователи инцидентов могут искать и выбирать готовые записи.
- Из формы инцидента можно перейти в справочник и вернуться с выбранным или новым
  объектом без потери черновика. Объект сохраняется отдельно от инцидента и может
  использоваться многократно. Отмена инцидента не удаляет запись справочника.
- После обновления панели выполните Ctrl+F5. Старые инциденты и связи не переносились.

## Права и хранение

PostgreSQL `incident_external_objects` — новый общий справочник по прямому решению
владельца; V82 только создаёт таблицу. Нет business_id, JSON fallback, внешних
вызовов, NetBox mutations или изменения feature flags. Создание требует глобальный
ROLE_ADMIN и доступ к инцидентам, проверяется controller/service + CSRF.
Бизнес-администратор/ALL_BUSINESSES не заменяет ROLE_ADMIN.

Native search требует соответствующий PAGE_DIALOGS/PAGE_TASKS/PAGE_OBJECT_PASSPORTS.
Business ticket дополнительно требует canonical business READ точного username;
legacy null-business остаётся в прежнем legacy-контуре. GENERAL и неоднозначные
raw ticket IDs не выдаются, включая коллизии с GENERAL. Поиск не является разрешением:
при создании/изменении связей объект повторно проверяется. Scope существующих
глобальных инцидентов, задач и паспортов не мигрировался.

IDs передаются строками; padded/Unicode ticket ID и bigint за границей JS Number
не преобразуются. Label внешней связи берётся из справочника, не из запроса.
Поиск дебаунсится; смена типа, ввод, reset, закрытие и выбор инвалидируют старые
ответы. Есть combobox/listbox, ArrowUp/Down, Enter, Escape, required validation,
экранирование текста и сохранение формы при ошибке POST. Стили — общие SCSS/tokens.

## Проверки

- 184 Java tests / 44 suites: failures/errors/skips=0; package GREEN.
  Дополнительно6PostgreSQL tests повторно GREEN после усиления проверки всех
  lookup SQL на полной реальной цепочке V1–V82 с настоящим BusinessAccessService.
- 46 JS unit tests +42 exact-ID assertions (6create,36transport), оба wrapper suites
  GREEN: node runner reports48tests. Повторены и в рабочем checkout.
- 170 browser combinations,190PNG: R11–R19, light/dark,320/390/768/1440/1920.
  Новый R19:10сценариев/30PNG, keyboard, abort/late response, четыре типа,
  большие/точные ID, обязательный выбор, admin create, readonly operator,
  XSS text, POST error/draft, независимое повторное использование справочника.
- Source audit74templates/47pages GREEN, inline-тем не добавлено.
- Изолированный PostgreSQL: новая миграция81→82, повторный startup без миграций,
  canonical label против подмены, две реальные incident relations на одну запись,
  rollback создания и изменения при несуществующем объекте, page/global admin
  guards, отсутствие обхода business scope и коллизий с GENERAL.
- Рабочий read-only Flyway preflight: все82resolved checksums valid,
  текущая81, pending только82. После migration old history hash прежний.
- Production: runtime healthy, JAR/static assets совпадают; anonymous страницы/API
  требуют вход. Bootstrap credentials в рабочем контейнере отсутствуют:
  авторизованный HTTP/UI smoke не выполнен, аккаунты/права не менялись.
  Реальные catalog/incident creation проверены на изолированной БД, не рабочей.

QA artifacts: `C:/Users/SinicinVV/git_h/iguana-r19-sandbox/spring-panel/target/incidents-r19-qa/`.
Они, операторы и backup остаются локальными ignored artifacts, не входят в commit.

## Source и deployment checkpoint

Baseline HEAD=origin/main=remote:
`3629b7220b68799406d322c3be712121c5ae8206` (R178), main был clean.
После sandbox GREEN guarded-applied22paths; дополнительно report/changelog,
итог24files. source_applied=true; staged/committed/pushed=false.
Общий task-status остаётся 🟣 до приёмки владельцем.

Рабочий Docker проверен заново и совпал с R18 container IDs, несмотря на заметку
другой сессии в S0. Использовано новое явное разрешение владельца на V82 и update.
Не развёртывался полный новый main: runtime — прежний R18 + ровно9JAR entries R19
(3class, V82, template,2JS, CSS, SCSS).2176→2180entries,4new, no removed;
остальные2171entry hashes прежние. Pending main server changes R177/R178 не включены.
Java class version61. Ошибки ранних prepare-image operators остановили упаковку,
не ослабляли guard и не затронули source/runtime; GREEN только prepare-image-v4.

Новый web:
- image tag `iguana-panel:incidents-r19-20261026`;
- image `sha256:6b1884e0734188bc59488e5f9eba2d078f18dcfc2a4662168cd34b23d9dbc8c6`;
- container `c9bf9d36b41f35edef41f3b31cd73b274794da3429513b1df2f3c5f0c041b19e`;
- started `2026-10-26T10:34:02.781236604Z`, healthy;
- JAR `3b2d5197a1dda652dc1cc66d111b33398512d9a920f8c7c40a1544c3c2a135ca`.
Default `iguana-panel:local` также указывает на этот образ.

Миграция выполнена одноразовым Flyway-only Java helper в удаляемом db-migrate
контейнере, без Spring context/bootstrap/jobs/repair/normalization/backfill.
Только V82; таблица пуста, тестовые бизнес-записи в production не создавались.
Сохранены18Compose containers: заменён лишь web, остальные17, включая worker,
не изменили IDs/images/start/env/mounts. Все4bot JVM PID/startTicks прежние.
Worker остаётся на R18; добавление таблицы/строкового relation type совместимо с
его generic relation reads, его автоматические сигналы не создают external relations.

Policy SHA `97d777785d26636b73a3ba798ada6e7976133416fa0da6156db154c2f95aa1d0`,
old Flyway history `7308dc877b5c173539d2b8d1b0778b7d9e305a43566135d96e1f4df30382e8de`,
4failed records `fc49912aad5b460a670b0c9233b9d84de0102bac1e71ac5625586bdfe557c88b`,
checkpoint cursors `5b49b9ad67f06aa7b96833a204bba72c0d7394b36121e25a3254ce319c96e887`
совпали до/после. Full web Env и4bind mounts сохранены, секреты в отчёты не писались.

Rollback image сохранён:
`iguana-panel:rollback-incidents-r19-web-20261026` →
`sha256:08ef0f54d43a02686180d42add337561efece4383badcaf7c664b868d1de3be0`
(JAR `ca6b7de3875230c78aaf12ce9da59ec9759f54c52599848ba2e44b6b6535fac6`).
Скрипт release.ps1 содержит auto rollback при неуспешном запуске; сам rollback
не выполнялся и не объявляется проверенным. При откате V82/новые данные сохранять,
не удалять таблицу. Старый UI не предлагает новый справочник.

Artifacts:
`C:/Users/SinicinVV/git_h/iguana-r19-sandbox/spring-panel/target/incidents-r19-image/`.
Там before/after.json, jar-validation.json, preflight/migration/deploy logs,
prepare-image-v4.ps1, release.ps1, runtime-evidence.cjs, migration-tools.
Backup `iguana-before-r19-20261026.dump`,9,486,563bytes,
SHA `a95508029c1b7bd31ef5274f7042b93bbd30e98b5de570c80eb87841129c87bb`;
копия в postgres `/tmp/iguana-before-r19-20261026.dump`, pg_restore --list1054entries.
Backup/rollback не удалять до приёмки; автоматическое восстановление БД не запускалось.

## Ручная приёмка владельца

1. Ctrl+F5 на /incidents; найти диалог по видимому номеру, задачу и паспорт;
   выбрать строку и создать нужный реальный инцидент.
2. Администратором создать настоящий внешний объект из общего справочника,
   привязать к инциденту; проверить повторное использование после reload.
3. Обычным пользователем проверить поиск/выбор без кнопки создания внешнего объекта.
4. Проверить, что отмена/ошибка не теряет черновик. Это не было выполнено
   агентом с вашей рабочей авторизацией.

## Точный manifest24files и команды владельцу

Этот блок агентом не выполнялся. Запускать из корня после просмотра diff.

```powershell
$r19Files = @(
    'ai-context/changelog/2026-10-26_133806_incident-related-objects-r19.md',
    'ai-context/rules/05-monitoring-policy-and-employee-pickers.md',
    'ai-context/tasks/task-details/01-281.md',
    'docs/ui/incident-related-objects-r19.md',
    'spring-panel/src/main/java/com/example/panel/controller/IncidentRelatedObjectController.java',
    'spring-panel/src/main/java/com/example/panel/service/IncidentRelatedObjectService.java',
    'spring-panel/src/main/java/com/example/panel/service/IncidentService.java',
    'spring-panel/src/main/resources/db/migration/postgresql/V82__incident_external_objects.sql',
    'spring-panel/src/main/resources/scss/app/_incident-workbench.scss',
    'spring-panel/src/main/resources/static/css/app.css',
    'spring-panel/src/main/resources/static/js/incident-relation-picker.js',
    'spring-panel/src/main/resources/static/js/incidents-workbench.js',
    'spring-panel/src/main/resources/templates/incidents/index.html',
    'spring-panel/src/test/java/com/example/panel/controller/IncidentRelatedObjectControllerWebMvcTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentClosurePostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentPriorityPostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentRelatedObjectPostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentServiceTest.java',
    'spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java',
    'spring-panel/src/test/js/incident-descriptions-r13.test.js',
    'spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js',
    'tools/incident-related-objects-qa.cjs',
    'tools/incident-workbench-qa.cjs',
    'tools/neutral-ops-source-audit.cjs'
)

git status --short
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала разберите существующий staging.' }
git add -- $r19Files
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
$r19Staged = @(git diff --cached --name-only)
git diff --cached --name-only
if ($r19Staged.Count -ne $r19Files.Count -or @(Compare-Object $r19Files $r19Staged -CaseSensitive).Count -ne 0) { throw 'Staging manifest mismatch' }
git commit -m "feat(incidents): добавить поиск связей и внешний справочник #ai"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }
git push
if ($LASTEXITCODE -ne 0) { throw 'Push failed' }
```
