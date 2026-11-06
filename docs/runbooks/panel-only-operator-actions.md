# Операторские действия только в панели — R295

Решение пользователя2026-11-06: «Операторские действия только из панели; группы —
для уведомлений». TG/VK/MAX — клиентские каналы. Действия оператора требуют
аутентифицированного panel actor и действующих business/GENERAL grants. Ни
внешний username, ни admin группы, ни подпись child API не заменяют эти права.
Управление каналами остаётся за системным администратором с правом страницы.

## Поведение

Telegram group/supergroup/channel и несоответствие private sender/chat owner
отбрасываются до dedup, поиска root/channel, history, ответа, download или CAS.
Удалены group /reply, text ticket reference relay, operator edit, auto registration
и /confirm. Бот не меняет аудиторию уведомлений по команде участника группы.
Группа не продолжает private questionnaire того же user. VK проверяет direct
positive sender=peer ещё до actor/claim/restore; группа не расходует private
delivery key. Частные BUSINESS/GENERAL ответы продолжают прежний workflow.

R294 сводки/relay клиентских сообщений/unblock/digest в configured service chat
остаются разрешёнными уведомлениями с fresh exact recipient admission.
Новый код не создаёт отдельную таблицу привязки внешних операторов.

| Retired internal route | Результат |
| --- | --- |
| POST /internal/api/bot/tickets/{ticketId}/reopen |410 control-plane /403 child |
| PUT/DELETE /internal/api/bot/tickets/{ticketId}/activity |410 /403 |
| POST /internal/api/bot/tickets/{ticketId}/operator-relay |410 /403 |
| PUT /internal/api/bot/tickets/{ticketId}/operator-messages/{id} |410 /403 |
| PUT /internal/api/bot/channels/{channelId}/support-chat |410 /403 |

Без валидной аутентификации —401. Backend сначала проверяет подлинность, затем
отказывает до mutation idempotency cache; ранее сохранённый200 не открывает
retired route. Child denied до ticket lookup, даже для своего канала/root.
Core HTTP helpers возвращают false/empty без HTTP. Local operator relay/config
отклоняются до SQL/JPA; operator edit возвращает false. Private client edit,
feedback/unblock submit, channel resolution и owned reads сохранены.
Trusted backend service methods не удалены: внешнего unowned route к ним нет.

## Проверки

460 latest successful targeted JVM cases/17 suites, failures/errors/skips0;
52 новых. Это bot matrix и backend matrix, не full project suite. Проверены:

- actual Telegram public ingress:14 invalid/group/foreign forms без I/O;
- restored TG(3)/VK(1) private selected sessions не меняются, затем принимают
  legitimate private answer; group event не захватывает VK claim;
-24 actual MVC/auth/local-cache cases:6 routes × no/wrong token/fresh/old success;
- child route deny до ticket read и сохранение собственных ticket reads;
- local/HTTP retirement до storage или network; read exact-ID tests сохранены;
-50 backend SQL regression cases на реальном isolated PostgreSQL schema,
  nonce-owned loopback fixture с нестандартным port, не на рабочей БД;
- прежние R294 exact sender/recipient/media fallback tests также GREEN.

Docker endpoint source-сессии desktop-linux/npipe недоступен. Native tests
используют существующий disposable fixture R292, который агент сам остановил;
свежие test schemas уникальны, nonce/loopback/port проверяются до тестов.
Две попытки нового initdb остановились по timeout; failures/logs сохранены,
ограничения тестов не отключались. Fixture вновь остановлен после проверок.
50 SQL cases, первоначально RED из-за Docker, повторены полностью GREEN native.
Protocol assertions прежних relay/edit tests заменены явным запретом по решению
пользователя; client/ownership assertions сохранены. Новый early private-owner
gate проверяет отсутствие I/O вместо прежнего более позднего exception.

## Сборка и выпуск на машине с проектом

Здесь выполнены source/test actions; рабочий проект/Docker — на другой машине
и в другой сессии. Migrations/provision/flags/working queues и providers не
использовались. BuildR293/sourceR292/imageff8c8f29d64d не содержит R294/R295 и
по checkpointR293 ещё не активирован. Нужны новый backend и все3 bot JAR с одним
core; перезапуск прежнего image не добавляет эти изменения. Новых migrations
R295 нет; V86/V87 и per-child SQL/Rabbit provision из R291/R292 всё ещё нужны
для общего выпуска. S2B снят, тестовые данные не требуют historical backfill.

На source commit после его переноса (JDK17; Docker нужен для SQL tests):

```powershell
Push-Location .\spring-panel
try {
    & .\mvnw.cmd test '-Dtest=RetiredOperatorApiTest,BotRuntimeWriteApiControllerWebMvcTest,InternalBotApiRequestGuardServiceTest,BotRuntimeTicketWriteServiceTest,BotRuntimeChannelServiceTest,ChildBotRequestScopeServiceTest'
    if ($LASTEXITCODE -ne 0) { throw 'Backend checks failed' }
    & .\mvnw.cmd package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Backend packaging failed' }
}
finally { Pop-Location }
Push-Location .\java-bot
try {
    & .\mvnw.cmd -f pom.xml -pl ':bot-telegram,:bot-vk,:bot-max' -am test '-Dtest=TelegramPrivateIngressTest,RetiredOperatorBoundaryTest,PanelTicketClientsExactLegacyIdTest,TelegramQuestionnaireSelectionTest,TelegramOwnerClientEditTest,VkQuestionnaireSelectionTest,TelegramSupportChatDeliveryTest,VkSupportChatDeliveryTest,SupportChatDeliveryTest,TicketServiceInboundTransportTest,ChannelServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
    if ($LASTEXITCODE -ne 0) { throw 'Bot checks failed' }
    & .\mvnw.cmd -f pom.xml -pl ':bot-telegram,:bot-vk,:bot-max' -am package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Bot packaging failed' }
}
finally { Pop-Location }
```

Далее использовать согласованный [R291/R292 release](child-transport-isolation.md)
на runtime-машине: backup/drain, старые children остановлены, backend/schema/
principals, затем новые children из одного image. Старый Telegram способен
сделать SDK send до API, поэтому backend410 сам по себе его не обезвреживает.
Не смешивать старые children с новым backend, не откатывать jars с запуском
непроверенных /reply handlers. Конкретный Compose project/file/env-file брать
из runtime checkpoint; эта source-сессия их не угадывает и deploy не выполняет.

На выделенном канале подтвердить: group /reply/edit/confirm/add-bot не меняют
клиента/историю/аудиторию; private questionnaire/media/edit продолжают работать;
panel operator с доступом отвечает, READ-only/revoked/foreign operator не может;
уведомления идут только в configured support chat. Это live acceptance, здесь
не выполнялось. Имеющиеся42 MAX +15 principal PG/Rabbit cases остаются готовым
следующим gate (не запускались здесь); S6/production-e2e-smoke §7.1 pending.

Локальные ignored proofs: .tmp/r295-baseline.json, r295-validation.json,
r295-result.json, r295-*-bot.log/r295-*-panel.log, r295-*-RED-*.xml,
r295-pg-state.json/r295-pg-stopped.json и r295-commit-commands.ps1.
