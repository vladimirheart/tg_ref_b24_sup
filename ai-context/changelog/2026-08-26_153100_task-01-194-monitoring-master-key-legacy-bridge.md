# 01-194 вЂ” compatibility bridge РґР»СЏ monitoring master key

- Р’СЂРµРјСЏ: `2026-08-26 15:31 +03:00`
- Р—Р°РґР°С‡Р°: `01-194`
- РџСЂРёС‡РёРЅР°: real observability runtime preflight РѕСЃС‚Р°РЅРѕРІРёР»СЃСЏ РёР·-Р·Р° РѕС‚СЃСѓС‚СЃС‚РІСѓСЋС‰РµРіРѕ shared monitoring master key.

## РџСЂРѕРјС‚ РїРѕР»СЊР·РѕРІР°С‚РµР»СЏ

```text
PS C:\Users\SinicinVV\git_h\tg_ref_b24_sup> powershell -ExecutionPolicy Bypass `
>>   -File .\finalize-01-194-observability-runtime-v1.ps1
...
Shared monitoring credentials master key must be overridden (MONITORING_CREDENTIALS_MASTER_KEY).
```

## Р§С‚Рѕ РёСЃРїСЂР°РІР»РµРЅРѕ

- РґРѕР±Р°РІР»РµРЅ explicit `base64:` mode РґР»СЏ `MONITORING_CREDENTIALS_MASTER_KEY`;
- СЌС‚РѕС‚ mode РёСЃРїРѕР»СЊР·СѓРµС‚ РіРѕС‚РѕРІС‹Р№ AES key material РґР»РёРЅРѕР№ 16/24/32 bytes;
- legacy `config/shared/monitoring-credentials.key` РјРѕР¶РЅРѕ Р±РµР·РѕРїР°СЃРЅРѕ РїРµСЂРµРЅРµСЃС‚Рё РІ ignored `.env` Р±РµР· СЃРјРµРЅС‹ РєСЂРёРїС‚РѕРіСЂР°С„РёС‡РµСЃРєРѕРіРѕ РєР»СЋС‡Р°;
- РѕР±С‹С‡РЅС‹Р№ configured master key СЃРѕС…СЂР°РЅСЏРµС‚ РїСЂРµР¶РЅСЋСЋ SHA-256 derivation semantics;
- РґРѕР±Р°РІР»РµРЅ regression test: ciphertext, СЃРѕР·РґР°РЅРЅС‹Р№ С‡РµСЂРµР· legacy key-file, СЂР°СЃС€РёС„СЂРѕРІС‹РІР°РµС‚СЃСЏ split-role service С‡РµСЂРµР· `base64:` master key;
- production contour runbook РґРѕРїРѕР»РЅРµРЅ migration note.

Commit/push РЅРµ РІС‹РїРѕР»РЅСЏСЋС‚СЃСЏ.