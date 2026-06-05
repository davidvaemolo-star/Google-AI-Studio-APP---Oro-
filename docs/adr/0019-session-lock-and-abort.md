---
status: accepted (builds on ADR-0017)
---

# A live Session locks the coach to the Training screen; the only early exit is a deliberate Abort

While a **Session** is live (`isActive` — Standby, Active, or Paused) the coach is **locked** to the Training screen: the bottom-navigation items (Programmes / Connection) are disabled, and the system Back gesture pops an **"End Session?"** confirmation rather than leaving. The only way out before the Zones finish is a deliberate **Abort** (the on-screen **End Session** button → confirm), which stops every device, plays a short stop cue (a brief beep + haptic on each paddle so the crew knows it stopped on purpose), and produces **no Session Outcome and no Crew Roll-Call**. The partial Session is still saved.

## Why

The navigation bar is always on screen, so a coach could tap away from Training mid-session. Nothing stopped the Session: it kept running in the background, completed its Zones, and fired the Crew Roll-Call while the coach thought they had left — and the screen still showed it as active. Locking navigation makes "am I in a session?" unambiguous, and routing every exit through a single deliberate Abort means a Session only ever ends one of two clearly-distinct ways.

## What we decided, and the trade-offs

- **Abort earns no Crew Roll-Call.** The spoken per-seat read-out (ADR-0016) is reserved for *completing* the programme; an early stop is not a result. Considered: playing the Roll-Call on any stop — rejected, because half-finished Sync/Power data would be misleading.
- **Abort is not silent.** A fully silent stop left the crew unsure whether the paddle had malfunctioned. A short distinct stop cue — a beep plus a triple-click haptic, reusing existing BLE events so no firmware reflash is needed — tells the crew it ended on purpose, clearly different from the "Session complete" sequence. The stop cue lives on the **Abort path only** — natural completion still plays "Session complete" → Crew Roll-Call, never the stop cue. (A dedicated descending "stop" tone would be a later firmware refinement.)
- **The partial Session is still saved.** Discarding 20 minutes of real paddling because the coach stopped early would surprise them; the Session is persisted, just without a Session Outcome.
- **Pause is not an exit.** A paused Session is still `isActive` and still locked.

## Consequences

- The existing Stop control becomes the **End Session** button and gains a confirmation dialog; the existing stop logic already saves and skips the Roll-Call, so the new work is the lock, the confirmation, the Back-gesture handler, and the Abort-only stop cue.
- New glossary term **Abort** (CONTEXT.md). "Cancel/quit/stop" are avoided in copy — the action reads **End Session**.
