# AGENTS.md (manul-backend)

Clojure/Lein REST API for the gig tracker.

## Run
```
cd /Users/farhan/code/manul-backend
lein ring server
```

- API: http://localhost:3000

## Database
- Local Postgres DB: `manul`.
- Env vars are set in Nushell config.
- All schema changes must be SQL migrations in `migrations/` and applied via `psql`.

## Conventions
- Prefer TDD: add or update tests before functional changes.
- Keep endpoint naming consistent ("performed live" vs "practise").
