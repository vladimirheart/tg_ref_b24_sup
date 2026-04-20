# Notion structure rendering and table of contents support

- `KnowledgeBaseService` больше не вырезает `table_of_contents` и `empty-block`, а переводит их в специальные markdown tokens для дальнейшего рендера;
- `KnowledgeMarkdownRenderer` научен постобрабатывать HTML: добавлять `id` заголовкам, строить блок `Оглавление` с якорями и рендерить пустые notion-блоки как визуальные отступы;
- heading anchors формируются детерминированно, чтобы ссылки из оглавления работали стабильно внутри статьи;
- `app.css` дополнен стилями для `knowledge-toc`, вложенных уровней оглавления и `knowledge-empty-block`;
- обновлены и очищены в UTF-8 тесты `KnowledgeMarkdownRendererTest` и `KnowledgeBaseServiceTest`, добавлена проверка на рендер оглавления.
