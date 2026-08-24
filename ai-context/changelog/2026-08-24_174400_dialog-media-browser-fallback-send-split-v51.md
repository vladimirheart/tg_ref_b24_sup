# 2026-08-24 17:44:00 вЂ” dialog media browser fallback + send split v51

## РџСЂРёС‡РёРЅР°

РџРѕСЃР»Рµ v50 СЂСѓС‡РЅРѕР№ browser smoke РїРѕРєР°Р·Р°Р», С‡С‚Рѕ image preview РЅРµ РѕС‚РѕР±СЂР°Р¶Р°РµС‚СЃСЏ, Р° voice `.ogg` РЅРµ РІРѕСЃРїСЂРѕРёР·РІРѕРґРёС‚СЃСЏ. Transport РїСЂРё СЌС‚РѕРј РїРѕРґС‚РІРµСЂР¶РґС‘РЅ СЃРІРµР¶РёРјРё Telegram/Rabbit Р»РѕРіР°РјРё, РїРѕСЌС‚РѕРјСѓ scope РѕРіСЂР°РЅРёС‡РµРЅ history URL -> attachment HTTP -> browser renderer/player.

## Р§С‚Рѕ РёР·РјРµРЅРµРЅРѕ

- raw nested attachment storage keys Р±РµР· metadata РїРµСЂРµРІРѕРґСЏС‚СЃСЏ РІ canonical `by-storage-key` URL;
- history renderer РЅРѕСЂРјР°Р»РёР·СѓРµС‚ legacy/raw attachment path РїРµСЂРµРґ РёСЃРїРѕР»СЊР·РѕРІР°РЅРёРµРј РІ `src`/`href`;
- image/audio/video СЃРѕС…СЂР°РЅСЏСЋС‚ direct HTTP РєР°Рє primary path, РЅРѕ РїСЂРё media error РёР»Рё `audio.play()` reject РїСЂРѕР±СѓСЋС‚ same-origin blob fallback;
- fallback РїРѕРєР°Р·С‹РІР°РµС‚ HTTP status С‡РµСЂРµР· РєРѕРјРїР°РєС‚РЅС‹Р№ error state Рё СЃРѕС…СЂР°РЅСЏРµС‚ СЃСЃС‹Р»РєСѓ `РћС‚РєСЂС‹С‚СЊ С„Р°Р№Р»` РЅР° РёСЃС…РѕРґРЅС‹Р№ URL;
- send-mode РїРµСЂРµРЅРµСЃС‘РЅ РІРЅСѓС‚СЂСЊ compact split-button `РћС‚РїСЂР°РІРёС‚СЊ в–ѕ`, Р±РµР· РїРѕСЃС‚РѕСЏРЅРЅРѕРіРѕ label/select/hint;
- `Ctrl+Enter` / `Enter` СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓСЋС‚СЃСЏ С‡РµСЂРµР· localStorage РјРµР¶РґСѓ legacy modal Рё workspace; `Shift+Enter` РѕСЃС‚Р°РІР»СЏРµС‚ РЅРѕРІСѓСЋ СЃС‚СЂРѕРєСѓ;
- РґРѕР±Р°РІР»РµРЅС‹ regression tests РґР»СЏ nested raw key Рё MVC media serving/range.

## РџСЂРѕРІРµСЂРєР° РїРѕСЃР»Рµ РїСЂРёРјРµРЅРµРЅРёСЏ

1. `spring-panel\\mvnw.cmd -q -DskipTests test-compile`
2. `spring-panel\\mvnw.cmd -q "-Dtest=AttachmentServiceMediaResponseTest,AttachmentControllerMediaWebMvcTest,DialogConversationReadServiceTest,PostgresqlRuntimeTemporalBindingTest" test`
3. `git diff --check`
4. `git status --short`
5. `powershell -ExecutionPolicy Bypass -File .\\tools\\release-readiness.ps1 -AllowDirty`
6. fresh browser smoke: JPG, Telegram animation `.mp4`, voice `.ogg`, video note `.mp4`, broken media fallback, both send modes + reload persistence.

Task `01-190` РѕСЃС‚Р°С‘С‚СЃСЏ рџџЈ РґРѕ СЂСѓС‡РЅРѕРіРѕ smoke.