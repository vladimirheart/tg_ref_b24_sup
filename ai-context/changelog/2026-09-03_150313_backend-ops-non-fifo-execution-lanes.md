# Backend ops non-FIFO execution lanes

Date: 2026-09-03_150313
Base main: ead02f605f5e11c515546ab998719f447341040a

## User request

со всем согласен, но 4-й пункт нужно сделать более широким: каждая подобная команда\запрос должна идти не по FIFO - это не надёжно.

- iikoServer location sync had been queued for hours although the actual sync normally takes seconds.
- The operator requested that this be fixed broadly: similar backend commands must not depend on one global FIFO queue.

## Production evidence before change

- iiko.locations.sync was queued with attempt_count=0 for more than three hours.
- The previous successful locations sync completed in less than one second after it was finally claimed.
- iiko.api.refresh was also queued for hours.
- One long ms.license.refresh owned the only synchronous dispatcher execution slot.

## Change

- Replace the single global FIFO execution slot with database-fenced execution lanes.
- Independent lanes run concurrently; only commands with a known shared write-conflict serialize.
- RMS license and RMS network refreshes share ms-monitoring because both update the same RMS monitor rows.
- iiko API, iiko locations, and NetBox passports use independent lanes.
- Add periodic durable heartbeat and attempt/owner/lane fencing for progress and terminal writes.
- Reduce stale-claim recovery horizon from two hours to five minutes.
- Fix iiko location sync UI semantics for queued/running state, actual start time, polling, and next-run hints.

## Deployment

- PostgreSQL migration adds unning_lane_key and a partial unique running-lane index.
- Worker and panel-web are recreated target-by-target; no broad compose down/up is used.
