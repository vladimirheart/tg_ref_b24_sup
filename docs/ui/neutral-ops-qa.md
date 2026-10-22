# Neutral Ops — реализация и проверка 01-281

## Что реализовано

Единый визуальный слой для 46 самостоятельных страниц Iguana: общие поверхности и
контролы, sidebar, настройки, диалоги и AI, клиенты/инциденты, dashboard/monitoring,
редакторы знаний, задачи/проекты, паспорта, вход/ошибки и подтверждение смены.
Изменения ограничены SCSS, производными CSS, подключением стилей в шаблонах,
связями label/input на входе и тестовым инструментарием.

`app/neutral-ops-*.scss` разделяет оформление по семействам. `style/neutral-ops`
задаёт нейтральные поверхности базовой палитры; Catppuccin и Amber сохраняются.
Disclosure-модули по-прежнему завершают соответствующие Sass entrypoint.

## Инвентарь и методика

`tools/neutral-ops-qa.cjs --inventory` проверяет все 73 шаблона: 46 страниц и
27 фрагментов. Каждая самостоятельная страница обязана иметь `data-ui-page`,
theme stylesheet и app stylesheet. Отсутствующий файл не пропускается молча.

| Семейство | Страниц |
| --- | ---: |
| analytics | 10 |
| auth | 1 |
| businesses | 6 |
| channels | 1 |
| clients | 3 |
| dashboard | 1 |
| dialogs | 2 |
| error, error/* | 4 |
| general | 4 |
| incidents | 1 |
| knowledge | 4 |
| passports | 2 |
| projects | 1 |
| settings | 1 |
| tasks | 1 |
| ui-kit | 1 |
| users | 2 |
| workforce | 1 |

Тестовый `NeutralOpsCaptureContextCustomizerFactory` активируется только свойством
`iguana.ui.capture` и экспортирует HTML после настоящего MVC-render. Он не входит
в production JAR. Публичные экраны и недостающие page families получают отдельные
тестовые модели; проверки маршрутов/разрешений остаются в существующих controller
tests, не заменяются этими моделями и не отключаются.

Браузер открывает HTML на локальном fixture-сервере с настоящими собственными и
vendor assets. Внешняя сеть и реальные записи запрещены; dashboard получает
явно тестовые данные графиков, остальные API могут показывать предусмотренные
empty/error states. Поэтому снимок с сообщением об ошибке API в fixture-режиме
сам по себе не означает сбой рабочей панели. Наполненные рабочие списки,
отправка сообщений, назначения и сохранение объектов требуют пользовательской
проверки; такие бизнес-действия в рамках визуального QA не выполнялись.

## Результат проверки

- Sass generation, Java compile и `package` прошли.
- 145 выбранных тестов: 0 failures, 0 errors, 0 skipped.
- 460 снимков: 46 страниц × 2 темы × 320/390/768/1440/1920 px.
- Дополнительные 72 снимка/проверки: 3 палитры × light/dark/auto × 2 viewport ×
  4 контрольных экрана. На 390 px включена настоящая эмуляция touch/coarse pointer.
- Нет выхода основного содержимого за viewport и отсутствующих theme tokens.
- У проверяемых заголовков и KPI контраст не ниже 4.5:1. Это измерение выбранных
  текстовых элементов, а не декларация полной WCAG-сертификации всего продукта.
- Невалидные поля и семантические таблицы отличаются от нейтральных поверхностей;
  основные touch-контролы не ниже 44 px; мобильное меню открывается/закрывается.
- 63 проверки открываемости модальных окон и удержания фокуса после Tab.
- Обзорные листы всех 46 экранов просмотрены в обеих темах; отдельные мобильные
  диалоги, редактор и workforce просмотрены в полном размере.
- `git diff --check` прошёл; исходники production Java/JS/SQL не менялись.

Артефакты находятся в `spring-panel/target/neutral-ops-qa/`: `inventory.json`,
`browser-report.json`, `states/browser-report.json`, отдельные PNG,
`review-light-1.html` … `review-dark-4.html` и live login smoke. Это производные
артефакты; `target` не коммитится.

## Воспроизведение

Из `spring-panel` запустить repository Maven wrapper:

```powershell
.\mvnw.cmd -q '-Diguana.ui.capture=target/neutral-ops-rendered' '-Dtest=DialogsControllerWebMvcTest,DashboardControllerWebMvcTest,AnalyticsControllerWebMvcTest,ClientsControllerWebMvcTest,BusinessPagesWebMvcTest,ManagementControllerWebMvcTest,KnowledgeBaseControllerWebMvcTest,GeneralTicketControllersWebMvcTest,*UiSourceContractTest,UiFinalHygieneSourceContractTest,WorkforceFoundationSourceContractTest,SettingsLocationsIikoSourceThemeSurfaceContractTest,NeutralOps*WebMvcTest,SettingsLocationsTreeHierarchySourceContractTest' package
```

Из корня проекта, с доступным Node-пакетом `playwright` (либо путём к уже
установленному Node-драйверу в переменной `PLAYWRIGHT_MODULE`):

```powershell
node tools/neutral-ops-qa.cjs --inventory
node tools/neutral-ops-qa.cjs
$env:IGUANA_UI_QA_OUTPUT = Join-Path (Get-Location) 'spring-panel/target/neutral-ops-qa/states'
node tools/neutral-ops-qa.cjs --states
Remove-Item Env:IGUANA_UI_QA_OUTPUT
```

В изолированном worktree Java sources нормализовались по LF для совместимости
существующих source-contract tests с Windows. Это не является изменением
production Java; при переносе проверяется именно content diff.

## Runtime checkpoint

Локальный `panel-web` обновлён на `iguana-panel:neutral-ops-r7-20261022-v2`:
`sha256:2d4d06151468fbe62cb5fcb310f1e9f681cf4fc0fc4bc7399edb0d775df35ca7`.
Здоровье — `healthy`. Проверены HTTP CSS bytes и реальный вход light/dark.
JAR SHA-256: `f171d18efc7cadd1d764b3bef68a794d087d8ddc40673ca3bfd022fa4abdde6b`.

Образ собран из проверенного локального JAR поверх уже загруженной runtime-основы,
чтобы обойти TLS timeout Docker Hub. Генерируемые Dockerfile и JAR сохранены в
`spring-panel/target/neutral-ops-image/`. Alias `iguana-panel:local` обновлён.
Метки образа явно указывают baseline `f96ba725d` и наличие незакоммиченного UI.
Это локальный rollout, не Git publish и не пользовательская production-приёмка.

Worker/bot-runner остались на прежнем image ID. Flyway version — 81 до и после;
`db-migrate`, backfill, внешние бизнес-системы и инфраструктура не запускались.

Сохранён rollback tag `iguana-panel:rollback-neutral-ops-20261022` с digest
`sha256:97ccb6fc7a34ae77af2eb7f264c49d8665393571060ad0fa46664ea528a9ce62`.
При необходимости, из корня проекта:

```powershell
$env:IGUANA_PANEL_IMAGE = 'iguana-panel:rollback-neutral-ops-20261022'
docker compose -p tg_ref_b24_sup --env-file .env -f docker-compose.production-contour.yml -f docker-compose.production-observability.yml up -d --no-deps --wait --wait-timeout 180 panel-web
docker tag iguana-panel:rollback-neutral-ops-20261022 iguana-panel:local
```

Откат не исполнялся: проверены наличие образа и сохранение ссылки на него.

## Фиксация source

Staging/commit/push не выполнялись. Точный список текущего изменения сохранён в
`spring-panel/target/neutral-ops-qa/changed-files.txt`; перед фиксацией сравните
staged paths с этим списком.

```powershell
$uiFiles = @(Get-Content spring-panel/target/neutral-ops-qa/changed-files.txt)
git status --short
git add -- $uiFiles
git diff --cached --check
git diff --cached --name-only
git commit -m 'feat(ui): распространить Neutral Ops на весь веб-интерфейс #ai'
git push
```
