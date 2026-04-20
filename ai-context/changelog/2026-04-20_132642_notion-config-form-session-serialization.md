# Notion Config Form Session Serialization

## Что сделано

- `KnowledgeBaseNotionConfigForm` переведена на `Serializable`, чтобы Spring Session мог безопасно сохранять flash-атрибут `notionConfig` после ошибки сохранения настроек.
- Добавлен `serialVersionUID` для стабильной сериализации формы между перезапусками приложения.

## Почему

- При неуспешном сохранении интеграции контроллер кладёт форму в flash-атрибуты через `RedirectAttributes`.
- Spring Session сериализует это содержимое в SQLite-backed session store.
- До исправления возникал `java.io.NotSerializableException: com.example.panel.model.knowledge.KnowledgeBaseNotionConfigForm`, из-за чего вместо возврата на страницу настроек отображалась общая страница внутренней ошибки.

## Ожидаемый результат

- После повторной попытки сохранить интеграцию страница должна корректно вернуться на экран базы знаний с сообщением об ошибке или успехе, без падения в generic internal error page.
