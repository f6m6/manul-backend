# manul-backend

REST API for the Manul gig tracker. This service reads from a local PostgreSQL database and is consumed by both UIs:

- **manul-frontend** (ClojureScript/figwheel, port 3449)
- **pallas** (React, port 3001)

## Quickstart

### Prereqs
- Java 8 (recommended for older Clojure tooling)
- Leiningen 2.x
- PostgreSQL 14 (Homebrew recommended)

### Database
This backend expects a local Postgres database (restored from your old Postgres.app data dir). The recovered DB name is `manul`.

Required tables/views (already present in the restored DB):
- Tables: `songs`, `performances`, `song_performances`, `venues`
- Views: `view_songs_per_date`, `view_song_plays`, `next_song`
- Compatibility views created for this backend: `view_next_songs_to_play`, `view_song_lengths_by_date`, `song_performance_dates`

### Run
In Nushell:
```
cd /Users/farhan/code/manul-backend

$env.GIGS_HOST = "localhost"
$env.GIGS_DATABASE = "manul"
$env.GIGS_USER = "postgres"
$env.GIGS_PASSWORD = ""
$env.GIGS_SSLMODE = "disable"

lein ring server
```
Server runs on `http://localhost:3000`.

### Useful endpoints
- `/all-songs`
- `/next-songs-to-play`
- `/next-active-songs`
- `/performances`
- `/venues`
- `/view-song-plays`
- `/view-song-plays-frequencies`
- `/song-performance-dates`
- `/normalised-count-per-day`
- `/last-gig-date`

## Relationship to other repos
- **manul-frontend** calls this API and expects the legacy endpoints listed above.
- **pallas** is an alternate UI that also uses this API.

## Notes
- CORS allows local frontend ports (3001, 3449).
- If you rename the DB to `gigs`, update `GIGS_DATABASE`.
