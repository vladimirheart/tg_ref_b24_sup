# Media menu encoding and animated sticker bubble follow-up

Date: 2026-08-24 18:47
Task: 01-190
Status: manual browser verification pending.

- Replaced newly injected Cyrillic menu literals with HTML numeric entities so Windows PowerShell 5.1 cannot corrupt them.
- Added an explicit compact sticker wrapper.
- Constrained TGS/Lottie and WebM sticker layout to the same 220px media footprint.
- Allowed sticker message bubbles to shrink-wrap instead of expanding from indefinite animation sizing.