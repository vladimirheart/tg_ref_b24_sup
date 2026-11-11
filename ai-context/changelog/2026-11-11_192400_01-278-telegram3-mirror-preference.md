# 01-278: сохранить выбранное зеркало Telegram3

Дата: 2026-11-11, Europe/Moscow.

Промт пользователя:

> Оставить зеркало, даже если бот пока не запустится

Это ответ на выбор маршрута Telegram3 «тостов»: сохранённое зеркало дало502
на getMe, а официальный API —200. Пользователь выбрал сохранить зеркало.

В карточке01-278, BOT_RUNTIME_CONTRACT, ai-data-boundaries и production-e2e-smoke
ожидание выбора заменено явным решением пользователя. Текущий маршрут Telegram3
сохранён; автоматический переход на официальный API не согласован.

Read-only проверка подтвердила прежние настройки канала3 и runner image33810b091477,
статус Telegram3 stopped, Telegram1/MAX2 running. После решения не выполнялись
перезапуски, сборки, deployment, provider calls, изменения settings/flags или
миграции. Evidence: R322/mirror-preference-proof.json вне Git.

Изменения только документальные, проверены в isolated worktree перед применением.
Предыдущий changelog R322 сохранён без изменений. Общая задача🟡 и пять S6 gates
остаются открытыми; partial fleet readiness не объявлена полным PASS.
Staging/commit/push не выполнялись.
