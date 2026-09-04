# 01-254 production verification follow-up

- Resolve/closed action больше не заменяет icon-only markup текстом.
- Send action сохраняет icon-only markup для обычного состояния, вложений и отправки.
- Во время отправки используется компактный spinner; описание состояния остаётся в aria-label/title.
- Блок «Проблема» показывает описание после «Уточнение после ответов на вопросы:» без служебного префикса.
- Backend/storage problem text не изменяется; исправлен только read-side UI.
- dialogs runtime cachebuster поднят до 20260904-3.
- 01-254 остаётся 🟣 до повторной ручной проверки production.
