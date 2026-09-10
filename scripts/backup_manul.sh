#!/usr/bin/env bash
set -euo pipefail

# cron and launchd start with a minimal PATH that omits Homebrew, so pg_dump
# is invisible to them. Prepend the usual Homebrew locations before use.
PATH="/opt/homebrew/bin:/usr/local/bin:${PATH}"
export PATH

if ! command -v pg_dump >/dev/null 2>&1; then
  echo "backup_manul.sh: pg_dump not found on PATH ($PATH)" >&2
  exit 1
fi

# Backup production DB to iCloud-synced Documents folder.
#
# Optional env:
#   PGUSER (default: postgres)
#   PGHOST (default: localhost)
#   PGPORT (default: 5432)
#   PGPASSWORD (default: empty)
#   SOURCE_DB (default: manul)
#   BACKUP_DIR (default: "$HOME/Documents/manul-db-backups")

PGUSER="${PGUSER:-postgres}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGPASSWORD="${PGPASSWORD:-}"
SOURCE_DB="${SOURCE_DB:-manul}"
BACKUP_DIR="${BACKUP_DIR:-$HOME/Documents/manul-db-backups}"

export PGUSER PGHOST PGPORT PGPASSWORD

mkdir -p "$BACKUP_DIR"

timestamp="$(date +%Y%m%d-%H%M%S)"
out_file="${BACKUP_DIR}/${SOURCE_DB}-${timestamp}.dump"
latest_symlink="${BACKUP_DIR}/latest-${SOURCE_DB}.dump"

pg_dump -d "$SOURCE_DB" --format=custom --no-owner --no-privileges --file "$out_file"
ln -sfn "$out_file" "$latest_symlink"

echo "Backup written: $out_file"
