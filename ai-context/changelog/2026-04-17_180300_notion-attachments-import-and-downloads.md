# Notion attachments import and article downloads

- добавлен server-side импорт вложений из Notion в `knowledge_article_files` при обновлении и создании статьи;
- поддержаны вложения из `files`-свойств страницы, из `file/pdf/audio/video` block types и из markdown-ссылок на скачиваемые файлы;
- для импортированных вложений добавлено стабильное имя хранения с префиксом `notion_<pageId>_...`, чтобы обновления не плодили дубли;
- устаревшие вложения, которые раньше были импортированы из Notion и исчезли из страницы, теперь удаляются из локального хранилища и БД;
- `AttachmentService` расширен внутренним API для сохранения и удаления файлов базы знаний без ручного browser-upload;
- в карточке статьи блок `Вложения` получил кнопку `Скачать` для каждого файла;
- тест `KnowledgeBaseNotionServiceTest` дополнен проверкой на извлечение PDF/ZIP ссылок из markdown;
- тестовый мок `DialogApiControllerWebMvcTest` обновлён под новый формат `AttachmentUploadMetadata`.
