#!/usr/bin/env bash
set -euo pipefail

# Creates a production-only secret file once. It deliberately refuses to
# overwrite an existing file, so repeated deploys never rotate live secrets.
if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <public-ip-or-hostname>" >&2
  exit 64
fi

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 69; }

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
target="$script_dir/.env.prod"
if [[ -e "$target" ]]; then
  echo "$target already exists; refusing to overwrite production secrets." >&2
  exit 1
fi

random_password() {
  openssl rand -base64 36 | tr -d '\n'
}

public_host=$1
mysql_root_password=$(random_password)
mysql_password=$(random_password)
redis_password=$(random_password)
minio_password=$(random_password)
jwt_secret=$(openssl rand -hex 64)

umask 077
temp_file=$(mktemp "$script_dir/.env.prod.XXXXXX")
trap 'rm -f "$temp_file"' EXIT

cat >"$temp_file" <<EOF
TZ=Asia/Shanghai
PUBLIC_HOST=$public_host

MYSQL_DATABASE=playmate_space
MYSQL_USER=playmate
MYSQL_ROOT_PASSWORD=$mysql_root_password
MYSQL_PASSWORD=$mysql_password

REDIS_PASSWORD=$redis_password

MINIO_ROOT_USER=playmate_minio
MINIO_ROOT_PASSWORD=$minio_password
MINIO_BUCKET=playmate-files
MINIO_PRIVATE_BUCKET=playmate-private-files

PLAYMATE_JWT_SECRET=$jwt_secret
PLAYMATE_JWT_EXPIRE_SECONDS=604800
PLAYMATE_SETTLEMENT_TRANSFER_ENABLED=false
PLAYMATE_FINANCE_CACHE_ENABLED=true
PLAYMATE_FINANCE_CACHE_TTL=15m

PLAYMATE_DB_HOST=playmate-mysql
PLAYMATE_DB_PORT=3306
PLAYMATE_DB_NAME=playmate_space
PLAYMATE_DB_USERNAME=playmate
PLAYMATE_REDIS_HOST=playmate-redis
PLAYMATE_REDIS_PORT=6379
PLAYMATE_REDIS_CONNECT_TIMEOUT=500ms
PLAYMATE_REDIS_TIMEOUT=500ms
PLAYMATE_MINIO_ENDPOINT=http://playmate-minio:9000
PLAYMATE_MINIO_BUCKET=playmate-files
PLAYMATE_MINIO_PRIVATE_BUCKET=playmate-private-files
PLAYMATE_MINIO_PUBLIC_BASE_URL=http://$public_host/minio

JAVA_TOOL_OPTIONS=-Xms192m -Xmx512m -XX:MaxMetaspaceSize=128m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai
EOF

mv "$temp_file" "$target"
trap - EXIT
echo "Created $target with mode 600. Store a secure backup before replacing the server."
