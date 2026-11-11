# 01-278 R320 — реальная Telegram BUSINESS приёмка

Дата: 2026-11-11, Europe/Moscow.

Промты пользователя:

- «готово» — после прохождения BUSINESS вопросника и отправки тестового файла.
- «Проверка DIRECT S6-TG-795118ad86c3: файл принят, прямое соединение Telegram работает.»

Изменены только README, карточка01-278, BOT_RUNTIME_CONTRACT, AI boundaries,
production-e2e-smoke и этот новый changelog. Приложение/боты не пересобирались:
пользовательский commit568df16a2 сверен с deployed bot inputs после CRLF/LF normalization.
Images/Compose/flags/V91 сохранены, migration/backfill не запускались.

Настоящий Telegram BUSINESS4 root сохранил выбранный scope/template/catalog v4.
Через Edge/admin проверены карточка/history/scoped скачивание154 bytes и полного
SHA256. Оба исходных подписанных AMQP wire адресно повторены с точными bytes/
typed headers/properties и тем же child broker identity. Независимый copy observer
подтвердил wire; root/history/media/inbox/outbox snapshots до/после прежние, DLQ0.

Первый panel reply получил502 mirror без Telegram ID. Он не повторялся.
После read-only getMe официальный API подтверждён, только channel1 platform_config/
network_route сохранены через admin PATCH, rollback snapshot защищён ACL.
Нормально перезапущен child1; сохранённый Redis questionnaire побайтно прежний,
каналы2/3 и их children не менялись. Отдельный DIRECT ответ ID10469 подтверждён
получателем; ledger success и одна operator history сохранены.

В BUSINESS4 через реальные формы подготовлены approved revision2 QA KNOWLEDGE,
полный review ответа и одно точное QA правило; текущий root AI control включён.
Входящий AI trigger/SENT/replay ещё ожидаются, правило пока активно. После штатного
auto_close тот же root возобновлён через панель без изменения общего таймера.
GENERAL и все пять общих S6 gates остаются открытыми; задача🟡.

Evidence вне Git — releases/r319-s6-telegram-20261111 под соседним iguana-runtime.
Private payload/credentials/snapshots/download/screenshot в Git не добавлены.
Sandbox от568df16a2, затем тот же validated metadata scope в primary;
staging/commit/push агентом не выполнялись.
