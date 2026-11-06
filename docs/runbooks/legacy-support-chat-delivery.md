# Legacy Telegram/VK уведомления в служебный чат — R294

SourceR294 продолжает пункт1 задачи01-278. Весь рабочий проект/Docker находится
на другой машине и в другой агентской сессии. Здесь выполнены только source
правки и изолированные JVM/loopback HTTP проверки. Рабочие DB/Redis/Rabbit,
provider tokens/получатели, containers и flags не использовались и не менялись.
BuildR293 на runtime-машине собрал imageff8c8f29d64d/sourceR292, оставив20 прежних
containers остановленными. Это исторический checkpoint другой сессии,
не наблюдение текущей source-машины и не активация R291/R292/R294.

## Поведение

Telegram и VK legacy сводки созданных обращений, relay ответов клиентов,
уведомления о запросах разблокировки и digest используют только canonical
channel.support_chat_id. APP_BOT_CHANNEL_ID/старые properties channelId служат
идентификатором записи панели и не могут подставляться как внешний получатель.
Нет support chat — нет отправки; сохранённая заявка/история этим не отменяется.
Для Telegram принимается canonical signed nonzero int64, включая negative group
ID; для VK — canonical positive int32 peer. Whitespace/leading zeros/plus/overflow
не нормализуются в другой внешний адрес.

SupportChatDeliveryService фиксирует channel/platform/token fingerprint/recipient
до I/O; перед каждым SDK handoff выполняет существующие uncached signed
authorize-sender и authorize-support-chat R291. Затем повторно сверяет локальные
channel/platform/active/token/recipient. Отозванный sender, смена привязки,
получателя или токена, old/unavailable backend и malformed decision запрещают
отправку. Target не переключается в новый чат на retry/fallback. API secrets и
provider tokens не включаются в target diagnostics.

Перед materialize Telegram file требуется admission, затем fresh admission
после file I/O и перед preferred SDK method. После явного Telegram400 возможен
document fallback с новой проверкой того же target. 403/429/5xx/transport failure
и потеря HTTP response не вызывают автоматический fallback/resend. Временный
файл очищается во всех завершениях, включая успешную preferred отправку.
Durable service-chat recovery/outbox этот шаг не добавляет; недоставленное
служебное уведомление не означает повторное создание или отправку клиенту.

MAX service-chat уведомления уже проходили MessagingService/R291 и не меняются.
Selected BUSINESS/GENERAL questionnaire, ticket ownership, rating/delivery
receipts и sourceLink R292 этим не переопределяются. Inbound group/operator
reply/confirm permissions — отдельный остаток, этот outbound gate их не закрывает.

## Сборка и включение на машине с проектом

Новый backend endpoint или миграция R294 не нужны. Для admission нужен backend
с R291 routes и работоспособным scoped child API; для всего выпуска R292 также
нужны V86/V87 и отдельный provision SQL/Rabbit principals. Следовать
[child transport](child-transport-isolation.md) и
[MAX linked media](max-linked-questionnaire-media.md): backup/drain,
coordinated backend/schema/principals и только затем новые children.
Не запускать R293 image как будто он уже содержит R294.

После фиксации/переноса source commit, из корня checkout с JDK17:

```powershell
Push-Location .\java-bot
try {
    $verifyArgs = @('-f', 'pom.xml', '-pl', ':bot-telegram,:bot-vk,:bot-max', '-am', 'test',
        '-Dtest=SupportChatDeliveryTest,TelegramSupportChatDeliveryTest,VkSupportChatDeliveryTest,PanelSupportChatAuthorizationTest,MessagingPlatformIsolationTest,MessagingSenderIdentityTest,MessagingObservedReceiptTest,TelegramQuestionnaireSelectionTest,TelegramOwnerClientEditTest,VkQuestionnaireSelectionTest',
        '-Dsurefire.failIfNoSpecifiedTests=false')
    & .\mvnw.cmd @verifyArgs
    if ($LASTEXITCODE -ne 0) { throw 'R294 targeted checks failed' }
    & .\mvnw.cmd -f pom.xml -pl ':bot-telegram,:bot-vk,:bot-max' -am package '-DskipTests'
    if ($LASTEXITCODE -ne 0) { throw 'Bot packaging failed' }
}
finally { Pop-Location }
```

Это сборка артефактов, не запуск. Core вложен во все3 bot jars: для согласованного
image пересобрать Telegram/VK/MAX из одного commit; сохранить JAR SHA256 и image
revision. Runtime-сессия должна включить новые JAR/image через свой установленный
bot-runner/Compose contour, сохранив backend R291+ и rollback. Только restart
панели или прежнего R293 image не добавит новый код. Команды чужого Compose
project здесь не подставляются; original project/file/env-file берутся из
checkpoint/снимков runtime-сессии. Source-проверки не разрешают запуск рабочего
стенда или migrations автоматически.

## Приёмка

На выделенном канале с собственными test clients проверить Telegram и VK:

| Сценарий | Ожидаемый результат |
| --- | --- |
| Legacy summary/active reply/unblock/digest | Только настроенный support_chat_id; ID записи панели не получатель |
| Telegram photo/document/другие media | Тот же разрешённый чат, bytes сохранены; temporary materialization очищен |
| Нет support chat/некорректный ID | Ни SDK send, ни подстановки channelId; media не materialize |
| Отзыв sender/channel или смена support chat | Старый target не отправляет и не следует в новый чат |
| Revoke/rebind во время materialize | Нет provider handoff после I/O |
| Явный отказ Telegram400 | Document fallback только в тот же разрешённый чат; revoke блокирует fallback |
| Неизвестный исход/потеря ответа/5xx | Нет автоматического повторного photo/document/text |

Зафиксировать channel/platform, source/image/JAR hashes, recipient decision и
provider result без токенов/личных сообщений. In-flight DB rebind между последним
API decision и самим provider send требует отдельного operational gate:
внешний SDK не участвует в транзакции backend. Эта source-проверка не доказывает
атомарность двух независимых систем.

## Проверено в source/test-сессии

430 fresh targeted JVM cases/10 suites, failures/errors/skips0,94 новых:
48 signed panel/target,28 actual Telegram SDK over loopback HTTP и18 VK SDK-double.
Прежние336 cases включают91 Telegram и90 VK selected questionnaire,9 actual
author edit, sender/platform/recipient/receipt contracts. JDK17/repository wrapper,
offline; main/test sources всех4 bot modules compiled. Нет доступа к рабочим
DB/broker/providers. Первые23 Telegram fixture errors сохранены отдельно: final
SDK execute нельзя подменить subclass Mockito; actual HTTP fixture заменил
подмену без отключения assertions. Финальный прогон всех10 suites единый GREEN.

Артефакты этой сессии: .tmp/r294-baseline.json, r294-v1-RED-*.xml,
r294-v1-RED-sources.json, r294-v1/v2/v3-bot.log, r294-validation.json,
r294-result.json и r294-commit-commands.ps1. Они локальные/ignored и не заменяют
tracked runbook; при их очистке тесты воспроизводятся командой выше.
Layout report — review signals прежних крупных классов, baseline не менялся.

PENDING: live provider/restart/rollback и cross-platform restore/replay/rebind;
42 MAX PG/Rabbit +15 principal Docker cases не выполнялись здесь. Local endpoint
desktop-linux/npipe недоступен; engine не запускался. §7.1
[production acceptance](production-e2e-smoke.md) остаётся MANUAL_PENDING.
Delivery/rating блок R289 заново не открывается. Общий пункт1 и S6 не закрыты.
