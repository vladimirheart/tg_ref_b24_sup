# 01-332 — Снять транспортные лимиты и читать ответ принимающей стороны

Время: 2026-11-27 17:48:48 МСК.
Baseline: 43659afd5fa24c96c62d2820fb6cc28a299caaeb.

## Промт пользователя

> ты писал:
> "Есть существенная деталь: сейчас Nginx ограничивает весь запрос 25 МБ, а Spring допускает файл до 50 МБ и запрос до 60 МБ. Лимиты сохраняю; ошибка будет указывать, какой слой отклонил загрузку и какой предел сработал."
> лимиты убери, но нужно читать только ответы либо от api ботов либо от прокси-сервера

## Изменения

- Nginx direct/HTTP/TLS templates: client_max_body_size 0; Spring multipart
  file/request и Tomcat form/swallow size=-1. Никаких подставленных 25/50/60 МБ.
- Перед отправкой Telegram убраны локальные пределы файла и подписи;
  отказ/ограничение читается из фактического API/proxy response.
- MAX upload init/binary upload сохраняют причину и код отказа. Ошибка загрузки
  прекращает отправку; неоднозначный сбой не повторяется через другой host.
- Области: Nginx, application.yml, DialogReplyTransportService,
  RestExceptionHandler, их тесты, настоящий UnlimitedMultipartTest, test logging,
  runbook message-upload-errors, правило 15 и task 01-332.
- Storage/DB limits анкеты/внутренней переписки не изменялись: по этому расширению
  scope направлен отдельный вопрос, ответа пока нет.

## Проверка и применение

Java 62/62, Node 13/13, skips 0. Реальный Tomcat: файл 61 МиБ + поле 3 МиБ;
изолированный Nginx: 61 МиБ и сохранённый upstream 413.
Wrapper package native exit 0, Java 17 и SRI GREEN.

Source применён после sandbox GREEN; stage/commit/push не выполнялись.
Развёрнут `iguana-panel:upload-unlimited-43659af-r332-20261127`,
image `sha256:a5630a94c2c64fbd7b8117552d9bbf930ee0ebb2d1f01e894268197788d7de48`.
Три роли healthy, три child JVM, 12 JAR hashes совпали. Nginx reload и HTTP smoke GREEN.
Env/mounts/Flyway 109 неизменны; миграций, backfill, ручного replay и тестовых
отправок реальным клиентам не было. Backup/прежний image r331 и rollback config
сохранены; реальный rollback не выполнялся. Задача 🟣.
