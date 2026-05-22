# 2026-05-22 12:00:50 - object passports list source fix

## Промт пользователя

`всё неплохо, но на основной странице паспортов объектов, http://127.0.0.1:8080/object-passports, список пуст, хотя паспорта еть, например: http://127.0.0.1:8080/object-passports/9 или http://127.0.0.1:8080/object-passports/1`

## Что изменено

- В `spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java` добавлен `listPassports()`, который читает реальные записи из `object_passports`, подтягивает `details` JSON и объединяет данные с `objects`.
- В `spring-panel/src/main/java/com/example/panel/controller/ManagementController.java` страница `/object-passports` переведена с `equipmentRepository.findAll()` на `objectPassportService.listPassports()`.
- В `spring-panel/src/main/resources/templates/passports/list.html` таблица каталога перестроена с полей оборудования на реальные поля паспорта: `Департамент`, `Город`, `Бизнес`, `Статус`, `Адрес`.
- В `spring-panel/src/test/java/com/example/panel/controller/ManagementControllerWebMvcTest.java` обновлён mock страницы каталога паспортов под новый источник данных.

## Зачем

- Корневая причина была в том, что каталог паспортов на самом деле рендерил список ИТ-оборудования, а не список `object_passports`.
- Поэтому при пустом `it_equipment_catalog` UI показывал пустой каталог, даже когда сами паспорта уже существовали и открывались по прямому URL.

## Проверка

- `spring-panel`: `./mvnw.cmd --% -q -DskipTests -Dmaven.resources.skip=true test-compile`

## Результат

- Страница `/object-passports` снова использует реальные данные паспортов объектов.
- Каталог больше не зависит от содержимого таблицы оборудования и должен показывать уже сохранённые карточки.
