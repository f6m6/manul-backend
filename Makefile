.PHONY: refresh-manul-test seed-manul-test backup-manul-now

refresh-manul-test:
	./scripts/refresh_manul_test.sh

seed-manul-test:
	./.venv/bin/python scripts/seed_manul_test.py --db $${TARGET_DB:-manul_test} --user $${PGUSER:-postgres} --host $${PGHOST:-localhost} --port $${PGPORT:-5432} --password "$${PGPASSWORD:-}"

backup-manul-now:
	./scripts/backup_manul.sh
