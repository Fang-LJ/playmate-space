#!/usr/bin/env sh
set -eu

cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

docker run --rm \
  -v "$(pwd)/certbot/conf:/etc/letsencrypt" \
  -v "$(pwd)/certbot/www:/var/www/certbot" \
  certbot/certbot:latest renew --no-random-sleep-on-renew --webroot -w /var/www/certbot

docker compose --env-file .env.prod -f docker-compose.prod.yml \
  exec -T playmate-nginx nginx -s reload
