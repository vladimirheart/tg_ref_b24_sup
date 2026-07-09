# 2026-07-09 12:55:43 - dialog-modal-media-send-and-encoding

## Что сделал

- Починил media-send в модалке диалога: pending-вложения теперь очищаются только после успешной отправки.
- Сохранил выбранные вложения при ошибке `/media`, чтобы оператор мог повторить отправку без повторного выбора файлов.
- Восстановил нормальный русский текст в затронутых modal runtime.
- Убрал лишний `return` в `dialogs-actions-runtime.js`, который обрывал обработку действий после открытия action-menu.

## Затронутые файлы

- `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-147.md`

## Контекст

```text
выбранные вложения отображаются, но не отправляются.
 
в модалке диалога есть места с битой кодировкой
```

## Проверка

- `rg -n "вЂ|РџС|Р |в|�" spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js spring-panel/src/main/resources/static/js/dialogs-presentation-runtime.js`
  Совпадений по типовым mojibake-паттернам в проверенных modal runtime не осталось.
- `node -e "const fs=require('fs'); ['spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js','spring-panel/src/main/resources/static/js/dialogs-actions-runtime.js','spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js','spring-panel/src/main/resources/static/js/dialogs-presentation-runtime.js'].forEach((file)=>{ new Function(fs.readFileSync(file,'utf8')); console.log('OK ' + file); });"`
  Все 4 файла успешно разобраны без синтаксических ошибок.

## Влияние

- Изменения ограничены runtime модалки диалога и не меняют backend-контракт `/api/dialogs/{ticketId}/media`.
- Логика работы с вопросами клиента боту не изменялась.
