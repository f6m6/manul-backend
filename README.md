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

There is also a `manul_test` database (a clone of `manul`) for safe migrations and endpoint verification.

### Production Backups
Take a one-off production backup (saved to `~/Documents/manul-db-backups`, which is on iCloud Drive):
```
cd /Users/farhan/code/manul/prod/manul-backend
./scripts/backup_manul.sh
```
or:
```
npm run backup:now
```

Install a daily cron backup (example: 02:30 every day):
```
crontab -e
```
Add:
```
30 2 * * * /Users/farhan/code/manul/prod/manul-backend/scripts/backup_manul.sh >> /Users/farhan/Documents/manul-db-backups/backup.log 2>&1
```

### Migrations
All DB changes must be done via SQL migrations (no ad‑hoc DB edits).

- Migrations live in `migrations/`
- Apply with:
```
psql -U postgres -d manul -f migrations/<file>.sql
```

For safe testing first, apply to `manul_test`:
```
psql -U postgres -d manul_test -f migrations/<file>.sql
```

### Reset + Seed Test DB
Rebuild `manul_test` with an identical schema to `manul`, then seed with synthetic data:
```
cd /Users/farhan/code/manul/prod/manul-backend
./scripts/refresh_manul_test.sh
```
or:
```
npm run db:test:refresh
```
or:
```
make refresh-manul-test
```

Seed only (without dropping/recreating):
```
npm run db:test:seed
```

Safety guard:
- `scripts/seed_manul_test.py` refuses any database other than `manul_test`.
- `scripts/refresh_manul_test.sh` refuses any `TARGET_DB` other than `manul_test`.

### Run
In Nushell:
```
cd /Users/farhan/code/manul/manul-backend

$env.GIGS_HOST = "localhost"
$env.GIGS_DATABASE = "manul"
$env.GIGS_USER = "postgres"
$env.GIGS_PASSWORD = ""
$env.GIGS_SSLMODE = "disable"

lein ring server
```

Server runs on `http://localhost:3000`.

To run against the test DB instead:
```
$env.GIGS_DATABASE = "manul_test"
lein ring server
```

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

## ADRs
Architecture decisions live in `docs/adr/`.

## Relationship to other repos
- **manul-frontend** calls this API and expects legacy endpoints like `/next-songs-to-play`.
- **pallas** is the current React UI.
