#!/usr/bin/env bash
set -euo pipefail

# Applies the current, non-destructive schema set to an existing database.
# It never drops the database, tables, rows, Docker volumes, or MinIO objects.
# Do not run p0_5_account_identity.sql here: the current p0_init.sql already
# contains its final schema, while that historical migration has unconditional
# ADD COLUMN statements for installations that predate P0.5.

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
repo_dir=$(cd "$script_dir/.." && pwd)
compose=(docker compose --env-file "$script_dir/.env.prod" -f "$script_dir/docker-compose.prod.yml")

"${compose[@]}" exec -T playmate-mysql sh -ec 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' <<SQL
$(cat \
  "$repo_dir/docs/sql/p0_init.sql" \
  "$repo_dir/docs/sql/p1_001_activity_collaboration.sql" \
  "$repo_dir/docs/sql/p1_002_activity_todo.sql" \
  "$repo_dir/docs/sql/p1_003_itinerary_poll_field_linkage.sql" \
  "$repo_dir/docs/sql/p1_004_expense_settlement.sql" \
  "$repo_dir/docs/sql/p1_005_activity_finance_state.sql" \
  "$repo_dir/docs/sql/p1_006_expense_proportional_split.sql" \
  "$repo_dir/docs/sql/p3_001_photo_wall.sql" \
  "$repo_dir/docs/sql/p3_002_photo_wall_hardening.sql" \
  "$repo_dir/docs/sql/p4_001_standalone_books.sql")
SQL
