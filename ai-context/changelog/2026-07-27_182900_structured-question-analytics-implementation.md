# 2026-07-27 18:29:00 — structured-question-analytics-implementation

## Что сделано

- начата реализация structured select-вопросов в шаблонах бота
- запускается сквозная доработка модели вопроса, runtime-сохранения ответов и dashboard-аналитики

## Зачем

- добавить ручной single-select помимо свободного текста и preset-полей
- сохранять ответы в универсальном виде для будущих аналитических срезов
- подготовить единый флаг участия вопроса в dashboard

## Итог реализации

- добавлен `select`-тип вопроса с ручными вариантами ответа в редакторе шаблонов
- добавлены унифицированные поля `binding_key` и `include_in_dashboard` для будущего масштабирования аналитики
- введено универсальное хранение structured-ответов в таблице `ticket_attributes` с миграцией и baseline-schema
- runtime telegram/vk/max сохраняет structured answers и промоутит системные ключи в совместимые поля `messages`
- dashboard read-side начал читать structured attribute `direction`, что даёт базу для дальнейшего вывода `business`, `bot_product` и других срезов

## Проверка

- `node --check spring-panel/src/main/resources/static/js/bot-settings.js`
- `spring-panel\\mvnw.cmd -q -DskipTests compile`
- `java-bot\\mvnw.cmd -q -pl bot-core,bot-telegram,bot-vk,bot-max -am -DskipTests compile`
