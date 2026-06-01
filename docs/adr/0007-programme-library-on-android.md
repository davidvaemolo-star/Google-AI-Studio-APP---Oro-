# ADR-0007: Programme library stored locally on Android; programme is a template

A Programme is a named, reusable ordered list of zones. For MVP, programmes are created and managed entirely within the Training Controller (Android app) — the Configuration Planner (web app) plays no role in programme creation at this stage. A library of named programmes is stored as a JSON file in app-local storage on the device. Room database was rejected as overkill for a flat list with no relational queries needed; JSON is simpler and portable if cloud sync or export is added later.

Loading a programme copies its zones into the current session — it does not keep a live reference. Edits made during a session (adjusting strokes, sets, or intensity on the fly) do not mutate the saved programme. This preserves the coach's designed programme across ad-hoc in-session adjustments.

A programme must be loaded before a session can start — ad-hoc zone editing without a programme is removed. On first launch, the app shows an empty state prompting the coach to create their first programme. The training screen always displays the name of the currently loaded programme.

The Programmes screen supports five operations: create, rename, delete, duplicate, and load.
