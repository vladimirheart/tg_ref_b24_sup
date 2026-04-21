## 2026-04-21 10:20:29

- для вложенных модалок редактора шаблонов бота и шаблонов оценок временно отключил интерактивность родительского `channelsModal`, чтобы его scroll и pointer events не конфликтовали с активной дочерней модалкой;
- добавил явный `overflow-y: auto` и `overscroll-behavior: contain` для `botTemplateEditorModal` и `botRatingTemplateModal`, чтобы прокрутка оставалась внутри активного окна.
