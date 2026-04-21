## 2026-04-21 16:42:34

- для `channelEditorModal` включил `modal-dialog-scrollable`, убрал зависимость от вертикального центрирования и зафиксировал высоту окна по viewport;
- добавил внутренний scroll в `modal-body` и `overscroll-behavior: contain`, чтобы прокрутка оставалась внутри активной модалки редактирования канала;
- на время открытия `channelEditorModal` перевёл родительский `channelsModal` в suspended-состояние, чтобы его слой не вмешивался в scroll и pointer events дочернего окна.
