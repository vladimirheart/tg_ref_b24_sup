# 2026-04-16 22:28:59 - employee-discount-url-profiles-and-live-iiko-runtime

## Что сделано

- контур `Bitrix24 + iiko automation` переведен на URL-зависимые iiko-профили пользователя вместо единственного конфига;
- `EmployeeDiscountAutomationCredentialService` переработан:
  - хранит несколько `iiko_profiles` по `base_url`;
  - хранит `active_iiko_profile_url`;
  - хранит выбранные категории и кошельки внутри конкретного URL-профиля;
- `IikoDirectoryService` переведен на documented iikoCard runtime:
  - получение `access_token` через `/api/0/auth/access_token`;
  - получение организаций через `/api/0/organization/list`;
  - получение категорий через `/api/0/organization/{organizationId}/guest_categories`;
  - получение кошельков/программ через `/api/0/organization/{organizationId}/corporate_nutritions`;
  - поиск гостя по телефону через `/api/0/customers/get_customer_by_phone`;
  - удаление категории через `/api/0/customers/{customerId}/remove_category`;
  - исключение из программы корпоративного питания через `/api/0/customers/{customerId}/remove_from_nutrition_organization`;
- `EmployeeDiscountAutomationService` и контроллер расширены endpoint-ом организаций iiko и запуском боевой обработки через активный URL-профиль;
- UI блока `AI Ops` обновлен под шаги:
  - сохранить URL и креды;
  - загрузить организации;
  - загрузить и отметить категории/кошельки;
  - сохранить профиль;
  - запустить dry-run или execute;
- обновлены `docs/bitrix24_iiko_discount_automation.md` и task-detail `01-033` под новую механику.

## Проверка

- `./mvnw.cmd -q -DskipTests compile` в `spring-panel` проходит успешно.

## Ограничения

- под “кошельками” в текущей реализации используется documented-модель программ корпоративного питания, которую еще нужно подтвердить на живом бизнес-сценарии;
- live-проверка на реальных URL, организациях и учетных данных еще не выполнена, поэтому задача пока остается в рабочем статусе до ручной валидации пользователем.
