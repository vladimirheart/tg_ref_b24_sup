# 01-278 — права страниц для API управления каналами, R181

Дата: 2026-10-26 (Europe/Moscow).

Промт пользователя:

> продолжай. пуш и коммит подтверждаю автоматически до конца суток

## Baseline и среда

Baseline R181 (2026-10-26): clean main, HEAD/origin/main/actual remote
f8a8367f333daf25cf0b2e3eb30cdbec2f7af120. R180 проверен по parent/subject/exact
10 paths/content SHA. Detached sandbox .tmp/01-278-r181. Рабочие проект и Docker
на другой машине/в другой сессии; здесь source и изолированные Maven MVC/service
fixtures. Docker/browser/Node в R181 не запускались. Рабочие revision/DB/config/
flags/providers неизвестны и не менялись; restart/deploy/migration/backfill
не выполнялись. Новый явный grant commit/push — до конца 26 октября Moscow,
2026-10-26 21:00 UTC (27 октября 00:00 +03). Source apply и отдельный publisher
разделены; publisher проверяет baseline/content/files/remote и expiry.

## Изменение

R181 вводит явную page authority для ChannelApiController,
ChannelBotCredentialApiController, ChannelTelegramDiagnosticsApiController и
ChannelNotificationApiController: hasAnyAuthority('PAGE_CHANNELS','PAGE_SETTINGS').
Это существующие права двух управляющих страниц: ManagementController /channels
требует PAGE_CHANNELS, settings API/редактор — PAGE_SETTINGS. Наличие одного
из этих прав сохраняет прежние controller→transport вызовы и ответы. Роль
ROLE_ADMIN, права других страниц и business/ALL_BUSINESSES tokens сами по себе
не заменяют page authority. Клиентский business_id тоже не даёт её.
Барьер закрывает GET/POST /api/channels, PATCH/PUT/POST/DELETE /api/channels/{id},
оба public-id/regenerate alias, bot credentials GET/POST/DELETE, test-message,
bot-info и channel-notifications — 14 route/verb combinations. Отказ происходит
до вызова транспорта, в том числе до загрузки credentials и внешней диагностики.
Настоящий CSRF для всех write aliases остаётся обязательным; GET не требует token.
Полный channel catalog больше не доступен любому authenticated пользователю;
sidebar без этих page rights получает прежний catch/hide при 403. Отдельного
расширения READ scope или нового публичного status endpoint здесь нет.
ChannelTransportService, DB/SQL, payload normalization, IDs, grants/roles, UI/JS,
schema/flags и internal bot APIs не менялись. Это global administration page
boundary, не business scoping каналов: foreign/shared channel CRUD policy,
membership/owner predicates и правила глобальных администраторов — следующий
отдельный gate. Остальные legacy/global picker/attribution, incident targets,
S2B и рабочий rollout/acceptance также открыты. Статус 01-278 — 🟡.

## Проверки

Проверки R181: 246 Java tests / 5 свежих XML GREEN, Maven v2 exit 0,
failures/errors/skips=0. Новый ChannelManagementAuthorityWebMvcTest — 170 cases
с реальной SecurityConfig, фильтрами, method security и CSRF. Для 14 routes
проверены 84 запрета при valid CSRF без нужной page authority, 28 разрешений
по PAGE_CHANNELS или PAGE_SETTINGS с exact delegation/response и long ID >2^53,
44 missing/invalid CSRF отказа и 14 anonymous redirects. Отказы не вызывают
mock transport. Session principal задаётся Spring Security test; live auth/DB/
provider acceptance не заявляется. Существующие 50 ChannelApiControllerWebMvcTest
с отключёнными фильтрами остаются функциональной регрессией; новый suite отдельно
доказывает security boundary. ManagementController (16), SettingsApiController (5)
и SettingsBridgeController (5) повторены без изменений.
До production edits тот же test source SHA на exact baseline blobs дал
170 tests / 84 failures / 86 passing controls / 0 errors/skips: чужие права
получали HTTP 200 вместо 403. XML/log/test SHA и controller blobs сохранены
в ignored r181-baseline-report.xml, r181-panel-v1.log, r181-baseline-red.json.
Тесты после RED не менялись. Кэш Java R179 использован только после сравнения
нормализованных SHA всех Java sources с текущим baseline; новый test и четыре
controller class реально перекомпилированы, XML свежие. Source/class/XML/log SHA,
generated CSS baseline и diff --check проверены. Node/browser=0: UI/JS не менялись;
прежний R53 debt из R180 здесь не запускался. Рабочей приёмки нет.

## Передача

- ai-context/changelog/2026-10-26_01-278_channel_management_authority_r181.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/java/com/example/panel/controller/ChannelApiController.java
- spring-panel/src/main/java/com/example/panel/controller/ChannelBotCredentialApiController.java
- spring-panel/src/main/java/com/example/panel/controller/ChannelNotificationApiController.java
- spring-panel/src/main/java/com/example/panel/controller/ChannelTelegramDiagnosticsApiController.java
- spring-panel/src/test/java/com/example/panel/controller/ChannelManagementAuthorityWebMvcTest.java

Guarded source apply переносит ровно 8 файлов после GREEN и заканчивается без
staging/commit/push. Отдельный publisher использует явный суточный grant.
Фактические source_applied/committed/pushed — ignored .tmp/r181-result.json
и итоговый ответ. Deployment=false; business-policy gate и общая задача открыты.
