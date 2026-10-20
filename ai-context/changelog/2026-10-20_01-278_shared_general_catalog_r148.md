# 01-278 — настройка GENERAL-вопросника общего канала и v2 preview (R148)

Дата: 2026-10-20, Europe/Moscow.

Промт пользователя: «продолжай».

Действующее уточнение: «продолжай. в текущей сессии не предоставляй команды для ручной фиксации».
Команды не предлагаются; staging/commit/push агент не выполняет. Рабочая копия
и экземпляр находятся на этой машине, restart разрешён, но не потребовался.

## Baseline и границы

HEAD/origin/main/actual remote: 405b80996abe03431a78417808bcbe15a391ffc9,
parent 37dd8b95ce10591d42fead6fa20fbcf3ea99515a. Проверен R147 (9 файлов),
исходные main/index чистые. Detached sandbox:
.tmp/01-278-r148-shared-general-catalog. Guarded apply использует тот же последовательный
apply_patch набор, exact manifest, pinned baseline blobs, отсутствие новых targets
и normalized UTF-8/LF SHA-256. Source-схема — V74; рабочая migration не выполняется.

## Выполнено

- Отдельная пустая V74 shared GENERAL binding table, без изменения tickets,
  existing channel settings, grants или legacy attribution. Distinct template ID,
  frozen SHA, FK и unique reservation; inactive binding не отдаёт шаблон бизнесу.
  Business и GENERAL-only writers также проверяют reservation и используют
  общую transaction-scoped advisory блокировку. GENERAL-only не расширяется.
- Защищённые GET/PUT/DELETE настройки требуют foundation, PAGE_SETTINGS и
  системного ROLE_ADMIN; мутации требуют CSRF. Business ADMIN/ALL_BUSINESSES не
  заменяют системного администратора. Bind/deactivate журналируют actor и IDs.
- Отдельные signed no-store v2 choices/selection: GENERAL с businessId=null и
  USER_SELECTION, свой вопросник и общий catalog SHA. Нет fake business 0,
  обязательных business/problem, global settings, locations или presets.
  Сохраняются question IDs/order; запрещены duplicate IDs/пустой flow/presets.
  Лимит 100 включает GENERAL. Изменения состава/имени/версии любого варианта
  требуют нового выбора. Active stale GENERAL закрывает весь v2 каталог.
- Preview меню не делает N повторных выборок всех business runtime payload;
  selection выдаёт ровно выбранный snapshot. PostgreSQL read snapshot использует
  REPEATABLE_READ; canonical file settings проверяются по SHA, не включаются
  искусственно в DB transaction. Возвращённые nested payload отделены от source.
- Удаление всех business bindings не открывает legacy runtime/INSERT для канала
  с active shared GENERAL, даже при выключенных flags. Для перехода на GENERAL-only
  требуется явное deactivate shared GENERAL. V1 API/SDK/боты совместимы отдельно.
- V2 forStart=true всегда закрыт без writer-version ACK, включая все flags=true.
  V73 writer не принимает v2 preview как capability записи. Readiness требует V74.

## Валидация

- 712 Java tests / 83 свежих Surefire XML: failures/errors/skips=0. Полный java-bot
  475/67 (core 217, Telegram 85, VK 68, MAX 105); backend 237/16. Новые suites:
  shared GENERAL PostgreSQL 29, admin HTTP/security/CSRF 11. Readiness 19,
  creation 39, GENERAL HTTP 74; остальные registry/ingestion/inbox/compatibility
  проверки сохранены и выполнены без ослабления assertions.
- Backend final: spring-panel/target/r148-panel-final-v4.log; fleet:
  java-bot/target/r148-fleet-final.log. В двух final isolated schemas применены
  все 74 migrations. Проверена свежесть 14 primary compiled classes относительно
  окончательных source. Node R117/R134 — GREEN.
- Проверены opt-in/disable/rebind, fingerprints/полный stale catalog, null versus
  fake owner, arbitrary question order, cross-scope template reservation и её
  конкурентное соблюдение, HMAC/query/flags/no-store, права/CSRF, предел 100 choices,
  запрет старта без ACK, запрет v2 GENERAL/BUSINESS INSERT и legacy downgrade.
- Промежуточные test compile/fixture ошибки исправлены в sandbox: типы тестов
  разрешены явно, каждый тестовый канал получает отдельный template ID; SQL
  unique constraint не ослаблялся. Финальный полный прогон — GREEN.
- Перед переносом проверяются pinned HEAD/origin/actual remote, baseline blobs,
  clean main/index и отсутствие новых targets. После того же apply_patch набора —
  exact manifest/hash/diff/index. Четыре generated Sass CSS совпадают с baseline
  Git blobs и не переносятся; иных исключений manifest нет.

## Осталось

V2 producer/INSERT/replay с immutable GENERAL provenance; затем v2 SDK и
GENERAL selection/session/menu в MAX/VK/Telegram. Это не готовый пользовательский
пункт «Без бизнеса» в ботах. Далее scoped locations/presets, provider/Redis/proxy,
rolling-upgrade/retention и сквозная приёмка. Другие шесть крупных блоков
01-278 остаются открытыми; задача 🟡.

Docker Server 29.7.2 заново подтверждён для изолированных Testcontainers.
Нет staging/commit/push/deploy, рабочих migrations/backfill/flags,
rebuild/restart/prune/cleanup, реальных provider sends и изменения rollback.

## Точный manifest (18 файлов)

- `ai-context/changelog/2026-10-20_01-278_shared_general_catalog_r148.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `spring-panel/src/main/java/com/example/panel/config/PostgresRuntimeReadinessVerifier.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedGeneralQuestionnaireAdminController.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedGeneralQuestionnaireRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessQuestionTemplateRegistryService.java`
- `spring-panel/src/main/java/com/example/panel/service/GeneralChannelQuestionTemplateService.java`
- `spring-panel/src/main/java/com/example/panel/service/SharedGeneralQuestionnaireService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V74__shared_channel_general_questionnaire.sql`
- `spring-panel/src/test/java/com/example/panel/config/PostgresRuntimeReadinessVerifierTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/SharedGeneralQuestionnaireAdminControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/BusinessQuestionTemplateRegistryPostgresIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/GeneralChannelQuestionTemplatePostgresIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/SharedGeneralQuestionnairePostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/SharedQuestionnaireRegistryPostgresTest.java`
- `spring-panel/src/test/java/com/example/panel/service/integration/SelectedBusinessTicketCreationPostgresTest.java`
- `spring-panel/src/main/java/com/example/panel/service/integration/BusinessTicketWriteScopeResolver.java`

