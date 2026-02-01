# manul-backend

REST API for the Manul gig tracker. This service reads from a local PostgreSQL database and is consumed by both UIs:

- **manul-frontend** (ClojureScript/figwheel, port 3449)
- **pallas** (React, port 3001)

## Quickstart

### Prereqs
- Java (Temurin 17 recommended)
- Leiningen 2.x
- PostgreSQL

### Database
This backend expects a local Postgres database named `manul` with tables/views already present.

### Migrations
All DB changes must be done via SQL migrations (no ad‑hoc DB edits).

- Migrations live in `migrations/`
- Apply with:
```
psql -U postgres -d manul -f migrations/<file>.sql
```

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

## Useful endpoints
- `/all-songs`
- `/next-songs-to-perform-live`
- `/next-songs-to-practise`
- `/performances-with-setlists`
- `/venues`
- `/albums/:id/tracks`
- `/practice-sessions`
- `/last-gig-date`

## Conventions
- Prefer TDD: add or update tests before functional changes.
- Keep naming consistent ("performed live" vs "practise").

## Relationship to other repos
- **manul-frontend** calls this API and expects legacy endpoints like `/next-songs-to-play`.
- **pallas** is the current React UI.
