# 2026-04-17 09:15:24 - улучшена диагностика источника Notion для импорта базы знаний

## Что изменено

- В `KnowledgeBaseNotionService` добавлена ранняя проверка значения `sourceUrl` при сохранении и при запуске импорта.
- Для корневого URL Notion и значений без UUID сервис теперь возвращает явную ошибку с подсказкой, что нужен `data_source_id` или ссылка на конкретную базу.
- Ошибка Notion `does not contain any data sources accessible by this API bot` теперь преобразуется в более понятное сообщение с рекомендацией открыть original database и подключить integration через `Add connections`.
- Добавлен тест `KnowledgeBaseNotionServiceTest` на валидацию источника и на переписывание ошибки про недоступный `data source`.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/KnowledgeBaseNotionService.java`
- `spring-panel/src/test/java/com/example/panel/service/KnowledgeBaseNotionServiceTest.java`
