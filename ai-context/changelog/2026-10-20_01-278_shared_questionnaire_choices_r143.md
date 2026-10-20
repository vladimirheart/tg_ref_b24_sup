# 01-278 R143: проверяемый выбор вопросника общего BUSINESS-канала

Checkpoint: 2026-10-20 12:39:14 MSK, sandbox validation GREEN.

Промт пользователя: «продолжай. рабочая копия проекта находится на этой машине, поэтому проект при необходимости можно перезапускать».

Baseline HEAD/origin/main/actual remote main:
0803565890a0c7b7695185be25c6dfd499d93e50, parent
4cc81f8a799f748063655762fa3ef00f0a9563f8. Проверены последний maintenance
commit (19 файлов) и R142 (15 файлов). Основной checkout чистый; реализация/
валидация — detached worktree. R137–R142 и независимая Docker-задача сохранены.

Scope — ровно 11 project files:

- `ai-context/changelog/2026-10-20_01-278_shared_questionnaire_choices_r143.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `java-bot/bot-core/src/main/java/com/example/supportbot/service/PanelRuntimeConfigClient.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/PanelRuntimeConfigClientTest.java`
- `java-bot/bot-core/src/test/java/com/example/supportbot/service/SharedQuestionnaireClientTest.java`
- `spring-panel/src/main/java/com/example/panel/controller/SharedQuestionnaireRuntimeController.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeConfigService.java`
- `spring-panel/src/main/java/com/example/panel/service/BusinessQuestionTemplateRegistryService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeConfigServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/SharedQuestionnaireRegistryPostgresTest.java`

Добавлен read-only handshake выбора BUSINESS: список вариантов канала с
version=1 и catalogSha256, затем selected runtime только для выбранного
businessId при совпадении SHA исходного меню. Каталог не содержит вопросников
или глобальных settings. Все active bindings проверяются: EXPLICIT_SELECTION,
active business/channel/template, distinct non-legacy template, valid unchanged
SHA, отсутствие general-only conflict, unsupported presets и duplicate IDs.
Лимит 100 вариантов; более крупный каталог требует отдельного pagination/UX.

Изменение видимого состава/названия/template content или rebind требует нового
выбора, а не recapture. BUSINESS/USER_SELECTION snapshot создаётся из того же
построенного read результата: ровно один detached template payload, пустые
locationTree/presetDefinitions. Existing internal token/HMAC и foundation +
shared-channel-resolution обязательны; оба ответа no-store. R112 endpoint
не меняет своей прежней семантики per-business resolution.

Primary unscoped runtime закрыт для канала с active business bindings, включая
flags-off: бизнес-опрос нельзя начать через legacy template. У общего клиента
ботов удалён cache между lookup; non-2xx/IO/malformed/unknown scope настроенного
API больше не возвращают local fallback. Disabled Panel API/другие transport
modes всё ещё требуют отдельного аудита. Это intentional fail-closed behavior;
такой source не разрешает rollout до совместимых UI/session/writer consumers.

Новые SDK choices/select подписывают path/query, валидируют channel/business/
version/catalog SHA и membership выбранного ID. Проверяются scope/source,
template ID/SHA и actual SHA-256 bytes единственного payload. Unknown versions/
scope, подмена текста, лишние global settings/locations/presets отклоняются.
SelectedQuestionnaire — отдельный тип, не legacy/general QuestionnaireScope;
business ID не отбрасывается неявным conversion. Consumer/UI integration ещё нет.

Валидация: 175/175 GREEN / 16 свежих Surefire XML без failures/errors/skips.
Backend 118: new PostgreSQL/actual signed MockMvc 21, R112 registry 2,
business runtime MVC 1, runtime service 4, primary runtime MVC 11,
general registry 2, general runtime MVC 1, internal guard 2,
full Spring GENERAL HTTP R142 74. Bot-core 57: new SDK 33, primary client 15,
headers 1, scope 2, general flow 2, legacy fallback isolation 1, session store 3.
Проверены HMAC query tamper, old menu/rebind/rename/membership/template changes,
mixed/stale/archived/duplicate/preset states, no-store, byte fingerprint,
отсутствие global/другого business payload и cache/fallback bypass.
Main/test compile всех bot modules Telegram/VK/MAX, full Flyway V1–V72 в
изолированном Spring context, два Node harness и diff check GREEN.
Freshness XML/compiled classes сверяется с final source своего компонента.

До GREEN: один native Maven launch не дошёл до tests из-за PowerShell
разбиения незакавыченного dotted -D аргумента. Аргумент заключён в кавычки;
повтор прошёл. После усиления SDK extra-settings case повторён final 57-case
bot run. Tests/security не ослаблялись; logs остаются в ignored target.

Уточнение среды обновлено по текущему промту. Fresh probes текущей сессии:
Docker Server 29.7.2; healthy panel-web/bot-runner/ops-worker/PostgreSQL.
Три приложения используют iguana-panel:sidebar-avatar-r120-da9694040092,
image ID sha256:9457b17ffac2692a3d03049f9f04d1942e692a16b92de0219428065018c4212c,
OCI revision da969404009276263c9b51bc362ce233ea6a7cf0 — не текущий source main.
Это новые наблюдения здесь, не перенос R137 evidence из другой сессии.
DB migrations/flags/functional acceptance/rollback readiness не проверены.
Разрешение необходимого restart учтено; restart не потребовался. Старые
checkpoint/changelog сохранены историей.

Перед source apply — fresh pinned baseline/clean checkout, exact file set и
normalized UTF-8/LF SHA-256. В main переносится та же последовательность
apply_patch после GREEN; generated Sass CSS не входит. Staging/commit/push/
deploy, рабочие DB migrations/backfill/flags, rebuild/restart/maintenance/
cleanup/prune не выполняются. PostgreSQL — отдельный Testcontainers, HTTP —
loopback fake server/MockMvc; реальные клиентские сообщения/providers не вызываются.

Задача 🟡. Кнопки Telegram/VK/MAX, явный GENERAL вариант общего канала, BUSINESS
scope через session/events/INSERT/replay, resume и scoped locations ещё PENDING.
Новый API не является writer authorization или сохранённой сессией. Остальные
GENERAL mutations, producer/child/recipient isolation, global/system/task policy,
AI/privacy/auto-reply, S2B owner decisions и выпуск остаются в текущей сводке.
