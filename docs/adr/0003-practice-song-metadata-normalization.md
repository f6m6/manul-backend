# 0003 Practice Song Metadata Normalization

- Status: Accepted
- Date: 2026-02-13

## Context

Solo practice songs need optional metadata (instrument, vocal mode, capo, metronome tempo(s), key changes, notes) without bloating core session tables.

## Decision

Introduce normalized tables:
- `instruments`
- `practice_vocal_modes`
- `practice_song_details`
- `practice_song_tempos`
- `practice_song_keys`

`practice_song_details` is keyed by `(practice_session_id, song_title)` and references
`practice_session_songs`, so metadata is tightly attached to existing per-song entries.

Tempo and key changes are separate child tables to support multiple values per song
without array blobs or denormalized text fields.

## Consequences

- Backwards compatibility: existing session payloads remain valid (metadata optional).
- Extensibility: the same normalization pattern can be reused for singing lessons and gigs.
- Data quality: constraints enforce bounded capo positions and valid BPM ranges.
