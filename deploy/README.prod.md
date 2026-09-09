# Playmate Space production deployment

Run production Compose from `/opt/playmate-space/deploy`. It publishes only Nginx port 80; MySQL, Redis, Spring Boot, MinIO API and MinIO Console are private Docker-network services.

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
