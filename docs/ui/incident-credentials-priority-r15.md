# Учётные данные и приоритет инцидента — R15

24.10.2026, задача 01-281. Статус 🟣: реализация и rollout завершены,
рабочая authenticated-приёмка владельца остаётся. Commit/push не выполнялись.

## Что было неверно

Read-only проверка канала #3 «тостов»: network_route.mode=profile,
profile_id=telegram-ftl-dev-ru-mirror, локальные username/password пусты.
Discovery считал наличие пустых ключей password/token в форме обязательными
учётными данными, не учитывая выбранный режим. Поэтому две локальные записи
получали missing_secret/critical, хотя приложение использует профиль.

В UI любой секрет назывался «ключом интеграции». Приоритет был статичным
в верхнем блоке, а dropdown находился ниже, в форме редактирования.
Сохранение этой формы существовало и не было сломано; подтверждённая серверная
проблема — автоматическое обновление могло перезаписать ручной приоритет.

## Изменения

- Название, тип данных, конкретный канал/профиль и путь настройки вместо
  абстрактного ключа. Пароль прокси не смешивается с токеном бота или VLESS.
  Поддержаны старые payload без signal_context, закрытые записи и escaping.
- Локальные proxy-поля нужны только при mode=proxy. Пароль требуется при
  заданном username, кроме VLESS; VLESS требует token. Пустые необязательные
  поля новых записей не создают. Ранее отслеживаемые строки сохраняются как
  not_required/ok, с прежними id и историей; возврат к обязательной настройке
  возобновляет обычную оценку. Не меняются маршрутизация и значения секретов.
- В верхнем блоке карточки — dropdown приоритета с немедленным PATCH и номером
  инцидента в уведомлении. Нижняя форма также сохраняет приоритет. Сохранение
  текста не закрепляет неизменённую автоматическую оценку. Draft не теряется,
  при неуспешном PATCH select возвращается к сохранённому значению.
- Ручной выбор записывается в существующий incident_events как priority_override,
  в одной транзакции с incident.severity, без миграции. Все существующие write
  paths IncidentService берут блокировку строки; refresh/resolution сигнала
  используют locked active lookup. После ожидания refresh читает сохранённый
  override. Публичный комментарий не может выдать себя за служебное событие.
  Автоматический приоритет нетронутых инцидентов продолжает обновляться.
- Сохранённая оценка мониторинга явно отделена от ручного приоритета.
  CSS/темы не добавлялись; используются общие компоненты.

Ручной выбор закреплён за этим инцидентом, не за всеми будущими инцидентами
с тем же сигналом. Исторические закрытые карточки и их исходная критичность
не переписывались массово: это история проверки, а не текущее состояние.
Приоритет исторической карточки оператор также может изменить.

## Проверки и guarded apply

- Fresh clean baseline HEAD=origin/main=remote
  d7418fb074c5a63d5e8de1b399fa2cdc401bf83a. Сначала detached sandbox
  C:/Users/SinicinVV/git_h/iguana-r15-sandbox, затем guarded apply ровно16
  source/test/tool/rule paths и сравнение нормализованных SHA256 каждого файла.
  Post-deploy metadata closeout добавил3 paths; итог19, staging отсутствует.
- Final package v5:230 Java tests /54 suites, failures/errors/skips=0.
  Среди них реальные Spring Data queries, Hibernate pessimistic locks и
  изолированная PostgreSQL: reload/new service, repeated refresh, manual/auto
  concurrency, transactional rollback при ошибке записи события, reserved
  event protection. Прежний closure transaction test сохранён.
- 9 applicability cases: profile/inherit/direct/vpn/пустой режим, no-auth proxy,
  VLESS, переход required→not_required→required, metadata edit, сохранение
  старых phantom rows при удалённом локальном proxy object.
- 32 JS unit cases +42 прежних exact-ID checks. Старый wording/cache contract
  обновлён на новый UI-контракт; immutability/security/closure assertions
  не ослаблялись.
- Браузер:10 новых priority scenarios, R13:130 состояний в10 combinations,
  R14:30 combinations, R12:10 workbench и R11:10 notifications scenarios.
  Light/dark ×320/390/768/1440/1920. Всего100 PNG.
  Проверены quick/full-form PATCH, reload, drafts, error rollback, отсутствие
  лишней severity в текстовом PATCH и горизонтального overflow.
- Source audit74templates/47pages GREEN; стили не менялись.
  Sass EOL-only outputs восстановлены только после semantic equality guard.
  Были исправлены две ошибки нового test harness при компиляции и null-safe
  ожидание перерисовки в новом browser harness. Рабочий checkout до GREEN
  не менялся; ошибки не обходились ослаблением behavior assertions.

Artifacts: ignored spring-panel/target/incidents-r15-evidence/
(100PNG, browser reports, MVC captures,54 XML, Maven/JS logs, runtime before/after,
jar-validation.json). Runtime helpers/rollback overlay:
spring-panel/target/incidents-r15-image/. Это локальные ignored артефакты,
не часть commit manifest.

### Воспроизведение

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/Users/SinicinVV/AppData/Local/Programs/Python/Python312/Lib/site-packages/playwright/driver/package'
.\spring-panel\mvnw.cmd -q -f spring-panel/pom.xml '-Diguana.ui.capture=C:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/target/neutral-ops-rendered' '-Dtest=Incident*Test,*Monitoring*Test,*CredentialRotation*Test,CredentialRouteApplicabilityTest,*ProviderDelivery*Test,IntegrationTransportIncidentMonitorTest,AlertmanagerIngestionServiceTest,NeutralOpsPageFamiliesWebMvcTest,ManagementControllerWebMvcTest,DialogsControllerWebMvcTest,Task*UiSourceContractTest,Task*SourceContractTest,WorkforceManagementUiSourceContractTest,UiNavigationDialogsSourceContractTest,FlywayConfigTest,MultibusinessFeatureFlagsTest,PostgresRuntimeReadinessVerifierTest,ChannelNotificationIdentity*Test,AlertQueueServiceTest' package
node --test spring-panel/src/test/js/incident-descriptions-r13.test.js spring-panel/src/test/js/incident-concrete-problems-r14.test.js spring-panel/src/test/js/incident-credentials-r15.test.js
node tools/incident-credentials-priority-qa.cjs
```

## Runtime checkpoint

Одна текущая Windows/Docker Desktop сессия этого checkout; не переносить её
факты на другую машину без fresh verification.

- panel-web и ops-worker: iguana-panel:incidents-r15-20261024,
  image sha256:75b3345638d47932a2145d9e8b5b6a363f97c15a08d106f4426a036815a9144f,
  оба healthy.
- Web container9ed1fda2a926425a3fc3e3db104bb954054cbb99d79d047f95d747a90bc2e508,
  start2026-10-24T11:47:14.43046676Z.
  Worker53335c346901b972e6315bd1f214ac22a85dee5da095fb44a8504bfdefa182ad,
  start2026-10-24T11:46:27.693653322Z.
- Оба runtime JAR побайтно равны проверенному Maven JAR:
  SHA256 568569f8fa6194913b5e7c85ac4951b25e3416c58e72d42a50407c9812bf577c.
  2176 entries,23 разных entry hashes:14 class entries только двух изменённых
  service families и IncidentRepository,5 изменённых UI resources и4 остальных
  resources с доказанным EOL-only различием. Libraries и прочие class hashes
  прежние. Все1385 application classes совместимы с Java17.
- Персональные service environments сохранены stdin-only overlay без сохранения
  секретов в файлы. Полные env hashes и bind mounts4/3 до/после равны.
  HTTP3JS и app.css совпали с packaged resources; protected endpoints
  без сессии возвращают302.
- Отдельный rollback web:
  iguana-panel:rollback-incidents-r15-web-20261024 →
  sha256:15e6544eab1f7a997ab2d11f97078032d264989f74eae68450f944e05313ee72 (R14).
  Worker:
  iguana-panel:rollback-incidents-r15-worker-20261024 →
  sha256:f34f1adbd276a4cee326d5b9a0b6a573b6ee736d8aee52eccd62fd7594185831 (R12).
  Наличие проверено, откат не выполнялся. Старые rollback tags сохранены.
  При откате старый registry снова может выдавать ложные local proxy alerts.
- Все13 исключённых project containers сохранили id/image/start, включая
  bot-runner; четыре Java PID и /proc start ticks ботов неизменны.
  Политика incident_monitoring.json сохранила SHA256
  97d777785d26636b73a3ba798ada6e7976133416fa0da6156db154c2f95aa1d0.
- FlywayV81 до/после. В startup logs обеих ролей — explicit skip migration.
  Migrator/infra/bots не перезапускались, schema migrations/backfill/flags,
  replay/requeue и ручные production incident/DB/external business writes
  агентом не выполнялись. Штатные worker jobs возобновились.
- Штатный registry scheduler в11:47:30Z уже обновил обе строки канала3:
  password/token missing_secret/critical → not_required/ok, source_present=true,
  secret_present=false. Значения не заполнялись и строки не удалялись.

Оставшаяся приёмка владельца: обновить страницу, проверить понятное описание
конкретной старой карточки, выбрать приоритет, перечитать карточку и проверить
его после следующего обычного refresh. Рабочие authenticated PATCH агент не
выполнял; persistence/concurrency доказаны на isolated PostgreSQL и browser
fixture, не выдаются за изменение реального инцидента пользователя.

## Точные19 файлов и команды владельцу

Следующий блок не запускался агентом. Сначала просмотрите diff. Он откажется
смешивать изменения с уже существующим staging; commit/push — действие владельца.

```powershell
$r15Files = @(
    'ai-context/changelog/2026-10-24_144925_incident-credentials-priority-r15.md',
    'ai-context/rules/06-incident-readable-descriptions.md',
    'ai-context/tasks/task-details/01-281.md',
    'docs/ui/incident-credentials-priority-r15.md',
    'spring-panel/src/main/java/com/example/panel/repository/IncidentRepository.java',
    'spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryService.java',
    'spring-panel/src/main/java/com/example/panel/service/IncidentService.java',
    'spring-panel/src/main/resources/static/js/credential-rotation-monitoring.js',
    'spring-panel/src/main/resources/static/js/incident-descriptions.js',
    'spring-panel/src/main/resources/static/js/incidents-workbench.js',
    'spring-panel/src/main/resources/templates/analytics/credential-rotation.html',
    'spring-panel/src/main/resources/templates/incidents/index.html',
    'spring-panel/src/test/java/com/example/panel/service/CredentialRouteApplicabilityTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentClosurePostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/service/IncidentPriorityPostgresqlTest.java',
    'spring-panel/src/test/java/com/example/panel/ui/IncidentWorkbenchCredentialRotationUiContractTest.java',
    'spring-panel/src/test/js/incident-credentials-r15.test.js',
    'spring-panel/src/test/js/incident-descriptions-r13.test.js',
    'tools/incident-credentials-priority-qa.cjs'
)
git status --short
$alreadyStaged = @(git diff --cached --name-only)
if ($alreadyStaged.Count -ne 0) { throw 'Сначала разберите существующий staging.' }
git add -- $r15Files
if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Staged diff check failed' }
$r15Staged = @(git diff --cached --name-only)
git diff --cached --name-only
if ($r15Staged.Count -ne $r15Files.Count -or @(Compare-Object $r15Files $r15Staged).Count -ne 0) { throw 'Staging manifest mismatch' }
git commit -m "fix(incidents): уточнить учётные данные и сохранить ручной приоритет #ai"
if ($LASTEXITCODE -ne 0) { throw 'Commit failed' }
git push
```
