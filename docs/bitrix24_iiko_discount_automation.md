# Автоматизация Bitrix24 + iiko

Документ описывает новый runtime-контур панели для сценария отключения
корпоративных скидок сотрудников.

## Что уже умеет контур

- работать из панели по кнопке;
- выполнять dry-run без боевых изменений;
- читать локальный machine-specific конфиг из
  `ai-context/parameters/local-machine/`;
- подключаться к Bitrix24 по webhook;
- находить группы и задачи;
- фильтровать только незакрытые задачи, в которых checklist-пункт еще не
  отмечен;
- вести историю запусков и результатов по каждой задаче в БД панели.

## Где находится UI

- страница `AI Ops`;
- блок `Bitrix24 + iiko automation`.

## Где хранить локальные секреты

Создайте локальный файл:

- `ai-context/parameters/local-machine/integrations.local.json`

Каталог уже закрыт `.gitignore`, поэтому реальные секреты не попадут в git.

## Рекомендуемый формат локального файла

```json
{
  "bitrix24": {
    "portal_url": "https://sushivesla.bitrix24.ru",
    "webhook_url": "https://sushivesla.bitrix24.ru/rest/1/xxxxxxxxxxxx/"
  },
  "iiko": {
    "group_name": "main-chain",
    "base_url": "",
    "token": "",
    "login": "",
    "password": "",
    "organization_id": "",
    "categories_url": "",
    "wallets_url": "",
    "customer_lookup_url": "",
    "customer_update_url": ""
  }
}
```

## Что хранится в versioned settings

В `config/shared/settings.json` сохраняются только non-secret настройки:

- `bitrix_group_id`;
- `task_title_markers`;
- `checklist_labels`;
- `phone_regex`;
- `selected_discount_category_ids`;
- `excluded_wallet_ids`;
- `dry_run_by_default`.

## Ограничения текущего этапа

- боевой iiko-мутатор требует точных endpoint-ов и payload-формата iikocard;
- до получения этих данных безопасно использовать discovery справочников и
  dry-run Bitrix24-отбора;
- checklist в Bitrix24 отмечается только после успеха по задаче.

## Что нужно для завершения боевого режима

- точный endpoint поиска клиента по телефону;
- точный endpoint изменения скидочных категорий и кошельков клиента;
- пример payload-а, которым нужно снимать категории скидок;
- пример payload-а, которым нужно исключать нужные кошельки;
- по возможности обезличенный пример request/response из рабочего iikocard-контура.
