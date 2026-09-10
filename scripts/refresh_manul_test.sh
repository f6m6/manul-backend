#!/usr/bin/env bash
set -euo pipefail

# Rebuild manul_test from manul schema, then seed with synthetic data.
#
# Optional env overrides:
#   PGUSER (default: postgres)
#   PGHOST (default: localhost)
#   PGPORT (default: 5432)
#   PGPASSWORD (default: empty)
#   SOURCE_DB (default: manul)
#   TARGET_DB (default: manul_test)

PGUSER="${PGUSER:-postgres}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGPASSWORD="${PGPASSWORD:-}"
SOURCE_DB="${SOURCE_DB:-manul}"
TARGET_DB="${TARGET_DB:-manul_test}"

if [[ "${TARGET_DB}" != "manul_test" ]]; then
  echo "Refusing to refresh non-test target database '${TARGET_DB}'."
  echo "Set TARGET_DB=manul_test."
  exit 1
fi

export PGUSER PGHOST PGPORT PGPASSWORD

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "Rebuilding ${TARGET_DB} from ${SOURCE_DB} schema..."
psql -d postgres -c "DROP DATABASE IF EXISTS ${TARGET_DB} WITH (FORCE);"
createdb "${TARGET_DB}"
pg_dump -d "${SOURCE_DB}" --schema-only --no-owner --no-privileges | psql -d "${TARGET_DB}"

echo "Ensuring local Python venv + seed dependencies..."
if [[ ! -d ".venv" ]]; then
  python3 -m venv .venv
fi
source .venv/bin/activate
python -m pip install --quiet Faker psycopg2-binary

echo "Seeding ${TARGET_DB}..."
python scripts/seed_manul_test.py --db "${TARGET_DB}" --user "${PGUSER}" --host "${PGHOST}" --port "${PGPORT}" --password "${PGPASSWORD}"

echo "Done."
