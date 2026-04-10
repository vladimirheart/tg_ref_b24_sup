# Readiness-проверка старта бота

- Время: `2026-04-10 11:47:41`
- Файлы:
  `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java`,
  `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`,
  `ai-context/tasks/task-list.md`,
  `ai-context/tasks/task-details/01-009.md`
- Что сделано:
  в `BotProcessService` добавлена реальная проверка готовности процесса после
  запуска с ожиданием Spring Boot маркера старта, ранним возвратом ошибок из
  `APPLICATION FAILED TO START`, обработкой таймаута readiness и cleanup
  неуспешного процесса; добавлены тесты на успешный старт, аварийный старт и
  таймаут; изменение отражено в `ai-context/tasks`.
