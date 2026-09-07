# 01-257 — client total time and resilient status options

- Client profile statistics now reuse the same total-time calculation already used by the clients list.
- ClientProfileStats exposes totalMinutes and formattedTime.
- Added «Затрачено времени» KPI to the profile statistics card.
- Client status dropdown no longer depends only on settings.json/client_statuses.
- Effective options merge configured statuses, color-map keys, known DB statuses, historical message statuses and the current profile status.
- Added an explicit «Статусы не настроены» empty state.
- app.css cache-buster updated to 20260907-3.
- Generated CSS is not edited directly.
- 01-257 remains 🟣 until production rollout and visual verification.
