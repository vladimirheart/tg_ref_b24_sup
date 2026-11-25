# 01-317 — Оценки, номера обращений и источники оповещений

Дата: 25.11.2026. Scope/checkpoint: tasks/task-details/01-317.md.

## Промты пользователя

> 1. из оповещений грохни оценки
> 2. при отправке в технический канал инфо о обращениях нужно отправлять его номер, а не id
> 3. при любом оповещении о диалогах, нужен его номер, а не id
> 4. в оповезениях необходимо визуальное разделение откуда от какого сервиса пришло оповещение

Уточнение источника: раздел панели («Диалоги», «Инциденты», «Задачи» и т. п.),
а для технического сообщения — ещё название канала/платформа бота.
Ответ пользователя: «да, делай».

## Изменения

- Feedback event больше не создаёт bell-уведомление. Старые оценки исключены
  из страниц, unread count и read mutations до пагинации; оценки/отчёты сохранены.
- Общий NotificationService нормализует ссылки на диалог до canonical номера
  при записи и чтении. SLA в технический чат, Telegram/VK/MAX summaries и
  пересылки ответов показывают номер; неподготовленный номер явно обозначается
  без UUID fallback. Регистр ID и точные URL/ownership guards сохранены.
- Добавлен общий bot formatter; technical сообщения отделяют источник, платформу
  и имя канала. Панель отдельно подписывает RMS/SSL monitoring сообщения.
- NotificationDto source/sourceLabel, UI badge/значок/цвет по разделу, безопасный
  fallback старого payload, новый cache marker sidebar.js.
- Обновлены regression tests под новый формат и действительный principal PG
  fixture, добавлены проверки источников, исторических оценок до pagination,
  canonical номера и exact-case ID. Дополнено правило 09; задача 01-317 — 🟣.

## Проверки и поставка

- Guarded source apply от fa7bcf68bcdc: sandbox GREEN; 219 panel tests,
  692 bot tests, 3 JS files, syntax/diff и Linux package/test-compile GREEN.
- Проверены installed panel JAR, Telegram/VK/MAX JAR и HTTP sidebar source.
  Readiness UP/healthy для panel-web, ops-worker и bot-runner; 3 child-бота запущены.
- Image: notifications-fa7bcf68bcdc-r317-r2-20261125-155356,
  digest sha256:daa98444d3528cb2600b9476e2130f4f0786eee24abecf5c7ca9126c964ddaf4.
  Остальные 12 project containers не изменились, schema V105.
- Сохранён rollback web r316 и worker/runner access-recovery; точные digests,
  контейнеры, Compose chain и артефакты указаны в task details и external checkpoint.
- DB migrations/backfill, физическое удаление оценок, ручные provider sends,
  staging/commit/push не выполнялись. Полная пользовательская приёмка остаётся ручной.
