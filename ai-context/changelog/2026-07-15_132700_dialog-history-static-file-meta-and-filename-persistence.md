# Изменение: статичный meta-блок файла в истории и сохранение исходного имени вложения

## Промпт пользователя

> внёс несколько правок, но такое-же поведение. и плюс ко всему отображается только расширение файла, без имени. давай наверное инфо о файле отображать статично в истории с названием и весом
>
> и в этой задаче я остановился не доделав её

## Что сделано

- В `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js` убран старый hover/info flow для вложений:
  - удалено позиционирование всплывающей info-панели;
  - история теперь использует только статичный meta-блок файла;
  - имя вложения нормализуется через decode/path cleanup и stripping UUID-prefix у operator uploads;
  - если в истории есть только технический hash без исходного имени, UI показывает безопасный fallback вида `Файл PDF`.

- В `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java` чтение истории расширено полем `file_name`:
  - `chat_history.file_name` подхватывается, если колонка уже есть;
  - при отсутствии readable filename применяется более аккуратный backend fallback.

- В `java-bot` добавлено сохранение `file_name` для новых вложений клиента:
  - `chat_history` schema и runtime `ChatHistoryService` теперь знают про `file_name`;
  - Telegram bot сохраняет исходное имя для `document`, `audio`, `animation`;
  - финализация conversation и active-ticket history пишут `file_name` в БД.

- В `spring-panel/src/main/resources/templates/dialogs/index.html` обновлён `dialogsAssetVersion`, чтобы браузер не держал старый JS кэш.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`
- `spring-panel/mvnw -q -DskipTests compile`
- `java-bot/mvnw -q -DskipTests compile`

## Что проверить руками

- В истории диалога у файла больше нет `i` tooltip и налезания на статистику.
- У новых файлов в истории виден статичный блок с названием и размером.
- Для новых клиентских Telegram-документов после перезапуска бота показывается исходное имя файла, а не только расширение.
