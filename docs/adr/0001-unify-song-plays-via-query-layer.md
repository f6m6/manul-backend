# ADR 0001: Unify Song "Played Anywhere" via Query Layer

Date: 2026-02-02
Status: Accepted

## Context
We track songs played across three sources:
- Live performances (`performances` + `song_performances`)
- Solo practice sessions (`practice_sessions` + `practice_session_songs`)
- Singing lessons (`singing_lessons` + `singing_lesson_songs`)

The "next songs to practice" feature should surface songs that have not been played
in any context. Changing the underlying DB schema to a single unified relationship
would be invasive and risky with existing data.

## Decision
Keep existing tables intact and unify "played anywhere" at the query layer.

We introduce:
- A small domain interface for song play queries.
- A DB-backed implementation that unions the three sources to compute
  "last played anywhere" and similar read models.
- Routes call the domain interface; no new SQL should be added to `handler.clj`.

## Rationale
- Minimal disruption to existing data and relationships.
- Clear boundary for future refactors (SOLID: dependency inversion).
- Allows incremental cleanup without blocking product progress.

## Consequences
- "Played anywhere" logic is implemented in the query layer, not the schema.
- If we later unify the schema, only the adapter needs to change.
- Adds a small amount of indirection but improves testability and separation.

## Follow-ups (Optional)
- Consider a DB view `song_play_events` if we need reuse in reporting.
- Evaluate a unified table once legacy data is fully stabilized.
