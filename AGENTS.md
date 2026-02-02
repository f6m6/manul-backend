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
- Use `manul_test` for schema changes and endpoint validation before touching `manul`.
- Env vars are set in Nushell config.
- All schema changes must be SQL migrations in `migrations/` and applied via `psql`.

Test DB notes:
- `manul_test` is a clone of `manul` for safe migrations and curl checks.
- Switch by setting `GIGS_DATABASE=manul_test`.

## Conventions
- Prefer TDD: add or update tests before functional changes.
- Keep endpoint naming consistent ("performed live" vs "practise").
