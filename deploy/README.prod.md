# Playmate Space production deployment

Run production Compose from `/opt/playmate-space/deploy`. It publishes only Nginx ports 80 and 443; MySQL, Redis, Spring Boot, MinIO API and MinIO Console are private Docker-network services.

## First deployment

```bash
cd /opt
git clone --branch main https://github.com/Fang-LJ/playmate-space.git playmate-space
cd /opt/playmate-space/deploy
./create-prod-env.sh 115.159.47.212
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build
```

The MySQL Docker entrypoint executes `docker-entrypoint-initdb.d` only when `playmate_prod_mysql_data` is empty. `down`, image rebuilds and container recreation retain all three named volumes. Do not use `docker compose down -v` in production.

`create-prod-env.sh` generates new MySQL root/application, Redis, MinIO and JWT secrets with `openssl`, writes `.env.prod` using mode 600, and refuses to overwrite an existing file. Keep an encrypted backup of that file outside the repository.

For real WeChat Mini Program login, add `PLAYMATE_WECHAT_APP_ID` and `PLAYMATE_WECHAT_APP_SECRET` to the server's `deploy/.env.prod`. The AppID must match the Mini Program project configuration. Keep the AppSecret only in the server environment file; `playmate-server` already loads this file through Compose `env_file`. Existing installations must add these variables manually before recreating the server container.

## Updating code

```bash
cd /opt/playmate-space
git pull --ff-only origin main
cd deploy
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --build --remove-orphans
```

## Existing database imports and migrations

Before importing an existing MySQL dump, create a timestamped backup of the target volume or dump. After import, run `./migrate.sh`; it applies only `CREATE ... IF NOT EXISTS` and guarded forward schema changes. It does not clear data. Historical `p0_5_account_identity.sql` is intentionally excluded because the current P0 initializer already contains its resulting schema and that old script has unconditional `ADD COLUMN` statements.

For a dump generated from a local container:

```bash
# local machine
docker exec playmate-mysql sh -ec 'mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --routines --events "$MYSQL_DATABASE"' > playmate_space.sql
# copy the dump to the server, then on the server
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T playmate-mysql sh -ec 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' < playmate_space.sql
./migrate.sh
```

MinIO data is held in `playmate_prod_minio_data`. To move it, use `mc mirror` while both endpoints are running, then verify object counts before switching traffic. MinIO metadata is kept inside that volume; do not copy individual filesystem files into a running MinIO container.

## HTTPS

After the A record for `api.playmatespace.cloud` resolves to this server, issue the first certificate with Certbot standalone mode while Nginx is stopped briefly:

```bash
cd /opt/playmate-space/deploy
mkdir -p certbot/conf certbot/www
docker compose --env-file .env.prod -f docker-compose.prod.yml stop playmate-nginx
docker run --rm -p 80:80 \
  -v "$(pwd)/certbot/conf:/etc/letsencrypt" \
  certbot/certbot:latest certonly --standalone \
  --email "YOUR_EMAIL" --agree-tos --no-eff-email \
  -d api.playmatespace.cloud
```

Then set `PLAYMATE_MINIO_PUBLIC_BASE_URL=https://api.playmatespace.cloud/minio` in `.env.prod` and recreate Nginx with the updated Compose configuration:

```bash
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d playmate-nginx
```

The HTTP server redirects to HTTPS except for the ACME challenge path. Renew manually with `./renew-cert.sh`; it uses the webroot path and reloads Nginx only after Certbot completes.
