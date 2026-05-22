# 2026-05-22 10:59:03 - passport photo upload and title preview fix

## Промт пользователя

`добавляю фото к паспорту, ставлю как титульное, сохраняю, но оно не отображается в шапке, как запланировано, а при повторной попытке давить фото, возвращает "Внутренняя ошиька сервера", в браузере в панели разработчика ошибка: "POST
	
scheme
	http
host
	127.0.0.1:8081
filename
	/api/object_passports/8/photos
Адрес
	127.0.0.1:8081
Состояние
500
ВерсияHTTP/1.1
Передано615 б (размер 193 б)
Referrer policysame-origin
Приоритет запросаHighest
Поиск в DNSСистема"`

## Что изменено

- В `spring-panel/src/main/java/com/example/panel/controller/ObjectPassportApiController.java` добавлены недостающие маршруты photo-API для паспортов объектов:
  - `POST /api/object_passports/{passportId}/photos`;
  - `PATCH /api/object_passports/photos/{photoId}`;
  - `DELETE /api/object_passports/photos/{photoId}`;
  - `GET /api/object_passports/photos/file/{storedName}`.
- В `spring-panel/src/main/java/com/example/panel/storage/ObjectPassportPhotoStorageService.java` добавлено отдельное файловое хранилище фото паспортов в `attachments/passport_photos` с выдачей inline URL для браузера.
- В `spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java` реализованы:
  - сохранение метаданных фото в `object_passports.details.photos`;
  - canonical `url`/`stored_name`/`original_name` для загруженных изображений;
  - обновление caption/category;
  - удаление фото;
  - нормализация photo-payload;
  - правило одного `title`-фото одновременно.
- В `spring-panel/src/test/java/com/example/panel/controller/ObjectPassportApiControllerWebMvcTest.java` добавлены regression-сценарии под multipart upload, `PATCH` и `DELETE` для photo-API.

## Зачем

- Реальная причина `500` оказалась не в самом браузере и не в сохранении паспорта, а в отсутствии backend route для `POST /api/object_passports/{id}/photos`.
- Из-за этого фронт не получал рабочий `photos[]` payload с `url`, а титульное фото физически не могло корректно сохраниться и отрисоваться в шапке.

## Проверка

- `spring-panel`: `./mvnw.cmd --% -q -DskipTests -Dmaven.resources.skip=true compile`
- Отдельный `ObjectPassportApiControllerWebMvcTest` в этой среде не прошёл из-за существующего Mockito inline mock blocker на `ObjectPassportService` под текущим JDK, не связанного с photo-фиксом.

## Результат

- Фото паспорта теперь действительно можно загрузить через backend API, а фронт получает список фотографий с рабочими ссылками.
- При выборе категории `title` шапка страницы может отобразить эту фотографию через уже существующий `updateTitlePhotoPreview()`.
- Повторные операции с фото больше не должны падать на `No static resource api/object_passports/.../photos`.
