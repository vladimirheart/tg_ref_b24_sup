# 2026-05-19 16:05:00 - css refactor and scss pipeline

## Промпты пользователя

```text
проведи рефакторинг css, при возможности пепеведя его в работу с scss.
```

## Что сделано

- Добавлен Maven-пайплайн компиляции `scss -> css` через `dart-sass-maven-plugin` в `spring-panel/pom.xml`.
- Создан новый слой исходников `spring-panel/src/main/resources/scss/` с входными файлами `style.scss`, `app.scss`, `sidebar.scss`.
- Текущие большие CSS-файлы разложены на частичные SCSS-модули по смысловым областям:
  `style/{fonts,theme,base,legacy}`,
  `app/{core,dialogs,unified-ui,knowledge}`,
  `sidebar/{shell,sections,notifications}`.
- Публичные пути `/css/style.css`, `/css/app.css`, `/css/sidebar.css` сохранены без изменений для всех thymeleaf-шаблонов.
- CSS-артефакты в `spring-panel/src/main/resources/static/css/` перегенерированы из SCSS и очищены от `.map`-артефактов.
- В `ai-context/tasks` заведена и закрыта в статус AI-review задача `01-097`.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- В проекте появился поддерживаемый SCSS-источник стилей без смены контрактов подключения.
- Дальнейший CSS-рефакторинг теперь можно делать по модулям, а не внутри трёх больших монолитных файлов.
