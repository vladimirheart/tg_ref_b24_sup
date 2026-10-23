# Инциденты и мониторинг — R12

23.10.2026, задача 01-281. Результат реализован и развёрнут локально;
статус 🟣, ручная приёмка владельца и Git publication не выполнены.

## Что изменилось

- В уведомлении смены статуса есть номер инцидента.
- Фильтры скрыты под кнопкой. Новый инцидент создаётся в модалке шириной
  до 580 px; названия заполненных полей остаются видны.
- «Основное» стало компактнее, приоритеты русифицированы, хронология свёрнута.
  Исправлен прыжок прокрутки при перезагрузке detail, включая reduced-motion.
- При ручном resolved/closed требуется комментарий: что конкретно сделано.
  Пустой/Unicode-blank/слишком длинный комментарий сервер отклоняет до mutation.
  Статус и resolution event записываются транзакционно; cancel не вызывает PATCH.
- Общий dropdown поиска сотрудников подключён к инцидентам, задачам, проектам,
  назначениям/участникам диалогов, бизнес/GENERAL forms, членству подразделений
  и канальным получателям. Сохраняет исходные id/name/value/FormData.
  Scoped select/datalist остаются серверным allowlist; глобальная выдача их
  не расширяет. Реальные контакты, внешний recipient username/ID бота,
  proxy credentials и создание аккаунта не являются employee selection.

## Где управлять мониторингом

«Инциденты → Мониторинг», изменение только ROLE_ADMIN + PAGE_SETTINGS с CSRF.
11 направлений: внешние подключения/каналы, public ingress, SMTP, SSL, RMS,
iiko, backup readiness, credential rotation, provider delivery,
integration transport и приём Alertmanager.

Включено; пауза на час/неделю/до даты; отключено бессрочно.
Пресеты отсчитываются от серверного времени; custom время явно обозначено
часовым поясом устройства. Нет изменения даты — сохраняется точный исходный instant.

Политика `incident_monitoring.json` хранится атомарно в общем shared-config
web/worker. Отсутствие файла сохраняет прежнее включённое поведение и не
создаёт файл. Повреждённый файл не включает проверки молча. Истечение срока
возобновляет направление без перезаписи. Остальные настройки отдельных
мониторов, feature flags и права не меняются.

Пауза останавливает нативные scheduler ticks до lease, ручные refresh,
queued backend operations и discovery соответствующего направления.
В batch есть повторная проверка между целями. Блокируются новые инциденты,
эскалации и уведомления; существующая история не удаляется. Старые outbox
записи откладываются без расходования attempts, после resume возможна их
обычная доставка. Уже начатая операция может завершиться.

Alertmanager здесь означает приём инцидентов: внешние Prometheus scraping
и evaluation настраиваются отдельно. Рабочая доставка сообщений, боты,
storage/coordination и критические liveness/readiness не выключаются.
Provider overview/credential snapshot, ранее запускавшие активные проверки
или discovery, при паузе возвращают явный 409, а не запускают их скрытно.
Старые images не понимают новый файл: при rollback паузы не гарантируются.

## Проверки и границы доказательств

- Final Maven package v9: 218 tests, 52 suites, failures/errors/skips=0.
  Это targeted regression, а не весь test suite репозитория.
- Изолированная Testcontainers PostgreSQL: сохранение status+resolution,
  reload и rollback при ошибке event; defer/resume outbox без delivery во время
  паузы. Production DB для fixture mutations не использовалась.
- Policy persistence/reinstantiation/expiry/timezone/corrupt-file/input tests;
  native manual/queued entry points и 9 schedulers до lease; permissions/CSRF
  и минимальный active-user DTO без credential/email leakage.
- Два JS regression scripts: 36 exact transport + 6 exact create checks.
  Старые assertions сохранены; create test получил DOM/modal doubles и
  дополнительные проверки reset/hide после перехода на настоящую форму.
- 38 browser page scenarios, 68 PNG: light/dark, 320/390/768/1440/1920,
  touch/reduced-motion; UI close/cancel/empty/comment/reload/toast number,
  collapsed filters/timeline, creation owner/watchers, search/no-results/Escape,
  native submit, pause week/custom unchanged exact instant/disabled,
  scoped opaque IDs без fetch глобальных кандидатов.
  Из них 18 page scenarios — реальные MVC task/project/settings HTML+JS
  с проверкой FormData, остальные — incidents + R11 notification regression.
- Source audit: 74 templates, 47 pages, общий first-paint style, без новых
  самостоятельных inline тем. Это source coverage, не 47 authenticated
  live-page acceptance сценариев. Ранее существующие structural/data styles сохранены.
- При разработке тесты нашли ошибочное помещение queue в create modal,
  focus race picker, недостающий method-security в MVC test slice и scroll
  clamp из-за reduced-motion transition. Исправлено до apply.
  Cancel assertion ожидает завершения hidden-event Promise, не ослаблена.
- 56 source/test/tool/rule paths после apply content-сверены с sandbox;
  CSS повторно собран Sass: контекст diff в повторяющемся CSS мог вставить
  блок раньше конца файла. После регенерации порядок совпадает с проверенным.
- JAR: 1811 BOOT-INF/classes entries совпадают с target, 1385 application
  classes совместимы с Java 17. HTTP 5 ресурсов побайтно равен packaged
  artifact; main source равен по содержимому с учётом LF/CRLF.

Артефакты не добавлять в Git:
`spring-panel/target/incidents-r12-evidence/` содержит три browser reports,
68 PNG, MVC captures, 52 XML/logs и runtime-acceptance.json.
Изолированная копия:
`C:/Users/SinicinVV/AppData/Local/Temp/iguana-incidents-r12-20261023`.
Проверочная база 6f6f4909d860da88362bf37aae1094f4318d25a2; во время работы
пришёл 7e6cc15453b477aba4944ef24b3054535622aeb9. Все 8 incoming paths
сверены и включены в финальную сборку, их Git changes не переписывались.

### Воспроизведение

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/Users/SinicinVV/AppData/Local/Programs/Python/Python312/Lib/site-packages/playwright/driver/package'
.\spring-panel\mvnw.cmd -q -f spring-panel/pom.xml '-Diguana.ui.capture=C:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/target/neutral-ops-rendered' '-Dtest=Incident*Test,*Monitoring*Test,*CredentialRotation*Test,*ProviderDelivery*Test,IntegrationTransportIncidentMonitorTest,AlertmanagerIngestionServiceTest,NeutralOpsPageFamiliesWebMvcTest,ManagementControllerWebMvcTest,DialogsControllerWebMvcTest,Task*UiSourceContractTest,Task*SourceContractTest,WorkforceManagementUiSourceContractTest,UiNavigationDialogsSourceContractTest,FlywayConfigTest,MultibusinessFeatureFlagsTest,PostgresRuntimeReadinessVerifierTest,ChannelNotificationIdentity*Test,AlertQueueServiceTest' package
node tools/incident-workbench-qa.cjs
node tools/employee-picker-qa.cjs
node tools/incident-notifications-qa.cjs
node tools/neutral-ops-source-audit.cjs
node --test spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js spring-panel/src/test/js/incidents-transport-exact-ticket-id-r34.test.js
```

Playwright путь локален; другой установленный Playwright разрешён через
PLAYWRIGHT_MODULE. Maven generate-resources создаёт derived CSS; не staging
случайные EOL-only изменения settings/sidebar/style.css.

## Фактический runtime checkpoint

Пользователь отдельно разрешил обновить оба сервиса вместе с backend main,
без миграций/flags и без ботов. Это **полная сборка Spring JAR**, не UI overlay.

- Source: main 7e6cc15453b477aba4944ef24b3054535622aeb9 + uncommitted R12.
- web и worker: `iguana-panel:incidents-r12-20261023`,
  `sha256:f34f1adbd276a4cee326d5b9a0b6a573b6ee736d8aee52eccd62fd7594185831`,
  healthy. web start 2026-10-23T12:42:51.178296545Z,
  worker start 2026-10-23T12:41:48.789182218Z.
- JAR SHA256:
  `8b94567ed74aed7ab66f8ccc5d3ebcf5768db0e25ca06f8b7ce8e603067fa433`,
  совпал внутри обоих контейнеров с проверенным artifact.
- Rollback web: `iguana-panel:rollback-incidents-r12-web-20261023` →
  `sha256:923bfd6cd1e5cb94e68c4de32650023e63c1bd989cdb97cc8b3dc0d2dd8afbea`.
- Rollback worker: `iguana-panel:rollback-incidents-r12-worker-20261023` →
  `sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`.
  Наличие проверено, rollback не исполнялся, прежние rollback tags сохранены.
- Разные текущие service environments сохранены stdin-only override без
  записи секретов. Guard проверил все environment keys/values и bind mounts
  (web4/worker3); общий shared-config bind совпадает.
- Build без Docker Hub pull, на локальном R11 JRE17 image; bot dist не менялся.
  Скрипты build/deploy/двухimage rollback в ignored
  `spring-panel/target/incidents-r12-image/`.
- Только `up --no-deps --no-build --pull never panel-web ops-worker`.
  Role WEB/WORKER явно пропустил Flyway strategy, V81 до/после. Миграций,
  repair/backfill нет; db-migrate и инфраструктура не пересоздавались.
- Feature flags не менялись; явных multibusiness env overrides не было и нет.
  bot-runner прежний image97ccb6fc…, start2026-10-22T12:43:42.881307558Z.
  Все 4 Java PID/start неизменны: 36153/37276/37955/38442.
- Агент не менял рабочие incident statuses/policy и внешние business-системы
  вручную. Файл policy не создан, настройки оставлены по умолчанию.
  После restart штатные worker jobs продолжаются; это не обещание остановки
  штатных внешних обращений.
- Anonymous /incidents, /api/incidents/monitoring, /api/panel-users/options=302;
  HTTP5 assets=200, SHA сверены. Authenticated рабочие изменения не тестировались
  агентом; fixture acceptance не заменяет приёмку владельца.

Владельцу: Ctrl+F5 на /incidents, проверить закрытие рабочего инцидента с
конкретным комментарием и его номер в toast; открытие/поиск наблюдателей;
«Мониторинг» → внешние подключения → пауза на неделю/бессрочно, затем
возобновление. Проверить, что web и worker не запускают проверки направления,
история сохранена, обычная доставка сообщений продолжает работать.
В рабочем экземпляре эти настройки агентом намеренно не переключались.

## Точный scope Git: 59 файлов

Git staging/commit/push не выполнялись. Сначала проверить diff; не добавлять
target/JAR/PNG/logs. Команды владельцу из корня checkout:

```powershell
$files = @(
  'ai-context/changelog/2026-10-23_154700_incidents-workbench-monitoring-r12.md'
  'ai-context/rules/05-monitoring-policy-and-employee-pickers.md'
  'ai-context/tasks/task-details/01-281.md'
  'docs/ui/incidents-workbench-r12.md'
  'spring-panel/src/main/java/com/example/panel/controller/IncidentMonitoringPolicyController.java'
  'spring-panel/src/main/java/com/example/panel/controller/PanelUserOptionsController.java'
  'spring-panel/src/main/java/com/example/panel/service/AlertmanagerIngestionService.java'
  'spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/BackupReadinessMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/CredentialRotationRegistryService.java'
  'spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/IikoApiMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/IncidentMonitoringPolicyService.java'
  'spring-panel/src/main/java/com/example/panel/service/IncidentRouteDeliveryOutboxService.java'
  'spring-panel/src/main/java/com/example/panel/service/IncidentService.java'
  'spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryAlertingScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/ProviderDeliveryAlertingService.java'
  'spring-panel/src/main/java/com/example/panel/service/ProviderHealthMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/ProviderHealthMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/PublicIngressMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/PublicIngressMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/SmtpNotificationMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/SmtpNotificationMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/SslCertificateMonitoringScheduler.java'
  'spring-panel/src/main/java/com/example/panel/service/SslCertificateMonitoringService.java'
  'spring-panel/src/main/java/com/example/panel/service/integration/IntegrationTransportIncidentMonitor.java'
  'spring-panel/src/main/resources/scss/app.scss'
  'spring-panel/src/main/resources/scss/app/_employee-picker.scss'
  'spring-panel/src/main/resources/scss/app/_incident-workbench.scss'
  'spring-panel/src/main/resources/static/css/app.css'
  'spring-panel/src/main/resources/static/js/auth-management.js'
  'spring-panel/src/main/resources/static/js/employee-picker.js'
  'spring-panel/src/main/resources/static/js/incident-workbench-ui.js'
  'spring-panel/src/main/resources/static/js/incidents-workbench.js'
  'spring-panel/src/main/resources/templates/businesses/dialog-assignment.html'
  'spring-panel/src/main/resources/templates/businesses/dialogs.html'
  'spring-panel/src/main/resources/templates/businesses/manage.html'
  'spring-panel/src/main/resources/templates/dialogs/index.html'
  'spring-panel/src/main/resources/templates/fragments/ui-head.html'
  'spring-panel/src/main/resources/templates/general/work.html'
  'spring-panel/src/main/resources/templates/incidents/index.html'
  'spring-panel/src/main/resources/templates/projects/index.html'
  'spring-panel/src/main/resources/templates/settings/index.html'
  'spring-panel/src/main/resources/templates/tasks/index.html'
  'spring-panel/src/test/java/com/example/panel/controller/IncidentPolicyAndUserOptionsWebMvcTest.java'
  'spring-panel/src/test/java/com/example/panel/runtime/WorkforceManagementUiSourceContractTest.java'
  'spring-panel/src/test/java/com/example/panel/service/IncidentClosurePostgresqlTest.java'
  'spring-panel/src/test/java/com/example/panel/service/IncidentMonitoringPolicyServiceTest.java'
  'spring-panel/src/test/java/com/example/panel/service/IncidentRouteDeliveryOutboxServiceTest.java'
  'spring-panel/src/test/java/com/example/panel/service/MonitoringDirectionGuardsTest.java'
  'spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java'
  'spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js'
  'tools/employee-picker-qa.cjs'
  'tools/incident-notifications-qa.cjs'
  'tools/incident-workbench-qa.cjs'
  'tools/neutral-ops-source-audit.cjs'
)
if (@(git diff --cached --name-only).Count -ne 0) { throw 'Сначала проверьте уже staged изменения' }
git add -- $files
git diff --cached --stat
git commit -m 'feat(incidents): обновить интерфейс и управление мониторингом #ai'
git push origin main
```

Общий статус задачи остаётся 🟣; 🟢 выставляет только владелец после приёмки.
