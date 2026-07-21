# 2026-07-21 08:47:38 — channel questions_cfg template cleanup

## Контекст
- Пользователь: `бери в работу следующий крупный шаг`
- Задача: `ai-context/tasks/task-details/01-150.md`

## Что сделано
- В `spring-panel/src/main/java/com/example/panel/service/ChannelTransportService.java` добавлено продвижение legacy template selection из `questions_cfg` в typed-поля канала `question_template_id` и `rating_template_id` на create/update.
- В ответах `/api/channels` и `/api/channels/{id}` `questions_cfg` теперь проходит повторную нормализацию, чтобы legacy-ключи выбора шаблонов не оставались частью публичного контракта.
- Нормализатор `questions_cfg` скорректирован так, чтобы отсутствие `fields` считалось валидным миграционным случаем и не ломало санитизацию/сохранение.
- В `spring-panel/src/test/java/com/example/panel/controller/ChannelApiControllerWebMvcTest.java` добавлены MVC-тесты на promotion legacy template ids и очистку `questions_cfg` в response.
- В `ai-context/tasks/task-details/01-150.md` обновлены текущая точка остановки и следующий крупный шаг.

## Проверки
- `spring-panel\mvnw.cmd -Dtest=ChannelApiControllerWebMvcTest test`
- `spring-panel\mvnw.cmd -DskipTests compile`

## Следующий шаг
- Убрать runtime fallback на `questions_cfg.active_template_id/question_template_id` и `questions_cfg.active_rating_template_id/rating_template_id` в `java-bot`, оставив только typed channel fields и временную диагностику миграции.
