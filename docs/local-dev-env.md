# 本地开发环境

本文档说明「玩伴空间小程序」本地 Docker 环境的启动、停止和排查方式。

## 环境组成

本地环境由 `deploy/docker-compose.local.yml` 管理：

| 服务 | 容器名 | 镜像 | 宿主机端口 | 容器端口 |
|---|---|---|---|---|
| MySQL | `playmate-mysql` | `mysql:8.0` | `13306` | `3306` |
| MinIO API | `playmate-minio` | `minio/minio:latest` | `19000` | `9000` |
| MinIO Console | `playmate-minio` | `minio/minio:latest` | `19001` | `9001` |
| Redis | `playmate-redis` | `redis:7.2-alpine` | `16379` | `6379` |

Redis 已加入 Compose，但放在 `optional` profile 中。P0 后端暂时不依赖 Redis，默认启动不会启动 Redis。

## 启动 Docker 环境

在项目根目录执行：

```bash
cp deploy/.env.example deploy/.env
```

然后启动本地服务：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
```

如果需要同时启动可选 Redis：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml --profile optional up -d
```

首次启动时 MySQL 会自动创建 `playmate_space` 数据库，并执行：

```text
docs/sql/p0_init.sql
```

该 SQL 会创建 P0 需要的四张表：

- `t_user`
- `t_file`
- `t_activity`
- `t_activity_member`

MinIO 初始化容器会自动创建 bucket：

```text
playmate-files
```

## 停止 Docker 环境

停止容器但保留数据：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml down
```

停止容器并删除本项目数据卷：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml down -v
```

注意：`down -v` 会删除本项目的 MySQL、MinIO、Redis 本地数据。

## 查看容器状态

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml ps
```

查看日志：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml logs -f
```

只看某个服务日志：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml logs -f playmate-mysql
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml logs -f playmate-minio
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml logs -f playmate-redis
```

## 连接 MySQL

连接地址：

```text
127.0.0.1:13306
```

数据库：

```text
playmate_space
```

普通用户：

```text
playmate
```

命令行连接示例：

```bash
mysql -h 127.0.0.1 -P 13306 -u playmate -p playmate_space
```

也可以使用 root 用户连接：

```bash
mysql -h 127.0.0.1 -P 13306 -u root -p
```

密码见 `deploy/.env`。`deploy/.env.example` 中的是本地开发示例密码，不要用于正式环境。

## 访问 MinIO 控制台

控制台地址：

```text
http://127.0.0.1:19001
```

账号和密码见：

```text
deploy/.env
```

实际启动时 Compose 读取的是 `deploy/.env`。如需调整账号、密码或端口，请先复制并修改 `deploy/.env`。

API 地址：

```text
http://127.0.0.1:19000
```

默认 bucket：

```text
playmate-files
```

## 确认 Redis 是否启动

Redis 是可选服务，默认启动命令不会启动 Redis。如需启动：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml --profile optional up -d playmate-redis
```

如果本机安装了 `redis-cli`，可以执行：

```bash
redis-cli -h 127.0.0.1 -p 16379 -a playmate_redis_dev_password ping
```

返回：

```text
PONG
```

也可以通过 Docker 查看：

```bash
docker exec -it playmate-redis redis-cli -a playmate_redis_dev_password ping
```

## Docker 安全边界

拉取镜像不会覆盖旧容器和旧数据。需要真正避免的是：

- 容器名冲突
- 端口冲突
- volume 共用

本项目已经使用独立容器名：

- `playmate-mysql`
- `playmate-minio`
- `playmate-minio-init`
- `playmate-redis`

本项目已经使用独立端口：

- MySQL：`13306 -> 3306`
- Redis：`16379 -> 6379`
- MinIO API：`19000 -> 9000`
- MinIO Console：`19001 -> 9001`

本项目已经使用独立数据卷：

- `playmate_mysql_data`
- `playmate_minio_data`
- `playmate_redis_data`

不要复用其他项目的 `mysql_data`、`minio_data`、`redis_data`。

## 为什么不会覆盖旧数据

Docker 镜像只是只读模板，拉取镜像不会覆盖已有容器数据。

本项目的数据写入独立 volume，不会写入其他项目的数据卷。只要不手动把其他项目的 volume 名称改成 `playmate_*`，就不会污染旧项目。

如果本机已有 MySQL、MinIO、Redis，只要它们没有占用本项目端口，就可以共存。

## 排查端口冲突

如果启动失败，先检查端口是否被占用：

```bash
lsof -i :13306
lsof -i :16379
lsof -i :19000
lsof -i :19001
```

如果端口被占用，可以修改 `deploy/.env` 中的宿主机端口，例如：

```text
MYSQL_HOST_PORT=23306
MINIO_API_HOST_PORT=29000
MINIO_CONSOLE_HOST_PORT=29001
REDIS_HOST_PORT=26379
```

修改后重新启动：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
```

## 常见问题

### MySQL 没有自动建表

MySQL 官方镜像只会在数据目录首次初始化时执行 `/docker-entrypoint-initdb.d` 下的 SQL。

如果之前已经启动过容器并生成了 `playmate_mysql_data`，后续修改 SQL 不会自动重复执行。开发阶段可以删除本项目 volume 后重启：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml down -v
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
```

### MinIO bucket 没有创建

查看初始化容器日志：

```bash
docker logs playmate-minio-init
```

如果 MinIO 尚未就绪，重新运行 Compose 通常会再次执行 bucket 初始化。
