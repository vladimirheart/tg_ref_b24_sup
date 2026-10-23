# Neutral Ops R11 — сообщения инцидентов без сдвига страницы

23.10.2026 · 01-281 · `🟣`, новые изменения ожидают ручной приёмки.

## Результат

Сообщения успеха и ошибки теперь появляются **поверх страницы**, сверху справа,
не занимая место между заголовком и вкладками. Появление — 180 мс, при
reduced-motion анимация отключена. На узком экране уведомление размещается
ниже мобильной кнопки меню; touch-крестик имеет область 44×44 px.
Используются существующие семантические alert colors и токены Neutral Ops.

Сохранены IDs `incidentWorkbenchError`/`incidentWorkbenchSuccess`, ARIA роли
и существующий textContent/classList контракт workbench/escalation producers.
Успех, как прежде, скрывается через 3500 мс. Ошибку можно закрыть крестиком;
закрытие **не удаляет DOM node**, поэтому следующее сообщение работает.
При появлении фокус не перехватывается; после закрытия клавиатурой он
возвращается к прежнему доступному элементу без прокрутки.

Изменение ограничено представлением: API, PATCH payload, статусные переходы,
авторизация, escalation logic и серверный код не менялись. Это не переход
всех уведомлений проекта на новый механизм. Блокирующий common alertdialog
с focus trap не переиспользовался: здесь требуется неблокирующее сообщение.

## Проверки

Baseline: `HEAD=origin/main=1f059a33265b37785a09df891a8e57bd48ff1d59`, чистый
исходный checkout. Сначала detached worktree
`C:/Users/SinicinVV/AppData/Local/Temp/iguana-ui-r11-20261023`, затем проверенный
scope перенесён и сверены все 7 source/test/tool files.

- Maven wrapper: Sass generation и 5 targeted Java tests,
  failures/errors/skipped=0. `NeutralOpsPageFamiliesWebMvcTest` проверяет
  семейства MVC-rendered страниц, включая новое подключение incident CSS.
- JS: два прежних exact ticket-ID regression scripts, 42 внутренних checks;
  syntax нового production/QA JS; source audit 74 templates / 47 pages — GREEN.
- Chromium: 10 combinations light/dark × 320/390/768/1440/1920 px, 20 PNG.
  Настоящие MVC HTML и workbench JS; PATCH перехватывается **локальным
  fixture-only API**, никакие рабочие инциденты не меняются.
- Success PATCH, error 503 и восстановление select; неизменные document
  coordinates main/tabs/list и scrollY при появлении, автоскрытии и закрытии.
  Проверены fixed positioning, bounds, отсутствие horizontal overflow,
  normal/reduced motion, touch sizing, keyboard close/focus, повторное
  уведомление после закрытия и длинный текст с прокруткой внутри сообщения.
- Визуально просмотрены desktop dark success и mobile light error.
  Browser page errors=0. Fixture acceptance не является authenticated
  проверкой владельца на рабочих данных.

Артефакты в ignored `spring-panel/target/neutral-ops-r11-notifications/`:

- [JSON проверки](../../spring-panel/target/neutral-ops-r11-notifications/report.json)
- [Desktop success](../../spring-panel/target/neutral-ops-r11-notifications/success-dark-1440.png)
- [Mobile error](../../spring-panel/target/neutral-ops-r11-notifications/error-light-390.png)

Повторить browser QA после MVC capture:

```powershell
$env:PLAYWRIGHT_MODULE = 'C:/Users/SinicinVV/AppData/Local/Programs/Python/Python312/Lib/site-packages/playwright/driver/package'
$capture = Join-Path (Get-Location) 'spring-panel/target/neutral-ops-rendered'
.\spring-panel\mvnw.cmd -q -f spring-panel/pom.xml "-Diguana.ui.capture=$capture" '-Dtest=NeutralOpsPageFamiliesWebMvcTest,IncidentWorkbenchCredentialRotationUiContractTest,IncidentApiControllerWebMvcTest' test
node tools/incident-notifications-qa.cjs
```

## Локальное обновление

Только `panel-web`, начало `2026-10-23T08:21:23.884930596Z`, healthy:

- Image `iguana-panel:neutral-ops-r11-20261023`.
- ID `sha256:923bfd6cd1e5cb94e68c4de32650023e63c1bd989cdb97cc8b3dc0d2dd8afbea`.
- JAR SHA `ab795a8fb0592844332710e78fc2494bd6228630a4fb1c9f32577189920af3b0`.
- Rollback `iguana-panel:rollback-neutral-ops-r11-20261023` — R10,
  `sha256:c36c7f2b8667ca6a8caa9619710f6a151158fdbcab61986e90c82b4aee1cc43d`.
  Наличие подтверждено, rollback не выполнялся; предыдущие tags сохранены.

Образ собран на локальной R10 основе без загрузки Docker Hub. В прежнем JAR
заменены/добавлены ровно три ресурса:

```text
BOOT-INF/classes/static/css/app.css
BOOT-INF/classes/static/js/incident-notifications.js
BOOT-INF/classes/templates/incidents/index.html
```

2022 → 2023 entries. SHA всех classes/libraries и остальных entries сохранены;
несвязанные backend commits main не поставлялись. Guarded packager и deploy
scripts находятся в ignored `spring-panel/target/neutral-ops-r11-image/`.
Environment и все 4 bind mounts до пересоздания совпали. Runtime JAR и
отдаваемые CSS/JS SHA сверены с проверенным scope, anonymous `/incidents`=302.

`ops-worker`/`bot-runner`: прежний image
`sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`,
прежнее начало `2026-10-22T12:43:42.885758443Z` / `...42.881307558Z`.
Workers/infra/db-migrate не перезапускались, Flyway 81 до/после;
migrations/backfill/external business writes не выполнялись.

Для приёмки: обновить `/incidents` через Ctrl+F5, изменить статус обычным
рабочим действием и проверить, что сообщение выезжает поверх страницы,
а вкладки, очередь и прокрутка остаются на месте. Ручная приёмка — владельцу.

## Точный scope и фиксация

10 файлов. Никакие target/JAR/PNG/logs в git не добавлять.
Staging/commit/push не выполнялись. Команды для владельца из корня checkout:

```powershell
$files = @(
  'spring-panel/src/main/resources/scss/app/_neutral-ops-feedback.scss'
  'spring-panel/src/main/resources/static/css/app.css'
  'spring-panel/src/main/resources/static/js/incident-notifications.js'
  'spring-panel/src/main/resources/templates/incidents/index.html'
  'spring-panel/src/test/java/com/example/panel/ui/NeutralOpsPageFamiliesWebMvcTest.java'
  'tools/neutral-ops-source-audit.cjs'
  'tools/incident-notifications-qa.cjs'
  'ai-context/tasks/task-details/01-281.md'
  'ai-context/changelog/2026-10-23_112252_incident-notifications-r11.md'
  'docs/ui/neutral-ops-incident-notifications-r11.md'
)
git add -- $files
git diff --cached --stat
git commit -m 'fix(ui): показать уведомления инцидентов поверх страницы #ai'
git push origin main
```

`🟣` сохранён; зелёный статус выставляет только владелец после проверки.
