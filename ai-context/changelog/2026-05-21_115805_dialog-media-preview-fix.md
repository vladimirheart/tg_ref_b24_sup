## Summary
- fixed legacy dialog attachment URLs for operator-sent media so preview requests hit the real ticket attachment endpoint instead of the missing `/api/dialogs/.../attachments/...` route
- added backward-compatible URL normalization for history records that still store attachment paths under `attachments/...`
- made `Esc` close the media preview modal before the parent dialog modal

## Prompt
`в модалке диалога если оператор отправляет медиа, например картинку, она не отображается как сама картинка, а в драузере возвращает:
GET
	http://127.0.0.1:8081/api/dialogs/361860bc-66ea-4327-9ada-58fd19fb1af5/attachments/e7894fce-1c19-4289-bebe-6c18b8071632_van-goga-podsolnuhi.jpg
Состояние
500
ВерсияHTTP/1.1
Передано707 б (размер 285 б)
Referrer policysame-origin
Приоритет запросаLow
Поиск в DNSСистема


если открыть на просмотр картинку присланную клиентом, то при нажатии на кнопку esc, закрывается модалка диалога, а не просмотр`

## What Changed
- in `DialogConversationReadService`, replaced legacy `/api/dialogs/{ticketId}/attachments/{filename}` URL building with the actual `AttachmentController` route `/api/attachments/tickets/{ticketId}/{filename}`
- in the same normalization layer, added a `by-path` fallback for history rows whose `attachment` column already contains stored filesystem-style paths under `attachments/...`
- updated `DialogConversationReadServiceTest` to assert the new operator URL and added coverage for path-based attachment rows
- in `dialogs.js`, added a capture-phase `Escape` handler that closes `dialogMediaPreviewModal` and stops the key event before it reaches the parent dialog modal
- registered the issue in `ai-context/tasks` as `01-101`

## Verification
- `spring-panel/./mvnw.cmd -q -DskipTests compile`
- `spring-panel/./mvnw.cmd -q "-Dtest=DialogConversationReadServiceTest" test` currently fails in this branch because unrelated existing test sources do not compile (`DialogDetailsReadServiceTest`, `DialogApiControllerWebMvcTest`, `DialogMacroControllerWebMvcTest`, `DialogServiceTest`, `SupportPanelIntegrationTests`)

## Why It Matters
- operator-sent images and other media in the legacy dialog modal now resolve to a live backend endpoint instead of a missing route that returns `500`
- older client attachment records remain readable without forcing a data migration
- `Esc` now behaves like users expect in nested media preview flow and no longer drops them out of the whole dialog by mistake
