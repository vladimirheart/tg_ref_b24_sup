# 2026-04-17 17:31:00 - очищены шаблоны knowledge от битой кодировки и изменён просмотр markdown

## Что изменено

- Шаблоны `knowledge/editor.html` и `knowledge/list.html` переписаны в чистом UTF-8 без mojibake-строк.
- На странице статьи отрендеренный markdown теперь показывается как основной контент статьи.
- Сырой markdown оставлен только в раскрывающемся блоке `Исходный Markdown для редактирования`, чтобы символы `#`, `*`, `[]()` и другие не мешали чтению статьи.
- В preview-экране импорта из Notion сохранён поток выбора статей перед загрузкой, но подписи и подсказки приведены к нормальной кодировке UTF-8.

## Затронутые файлы

- `spring-panel/src/main/resources/templates/knowledge/editor.html`
- `spring-panel/src/main/resources/templates/knowledge/list.html`
