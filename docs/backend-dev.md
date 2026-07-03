# 后端本地启动说明

后端工程使用 Java 21 + Spring Boot 3.x + Maven + MyBatis-Plus。

## 前置依赖

- Java 21
- Maven
- MySQL 本地 Docker 环境已启动

启动 MySQL 和 MinIO：

```bash
docker compose --env-file deploy/.env.example -f deploy/docker-compose.local.yml up -d
```

## 数据库连接

本地开发配置在 `playmate-server/src/main/resources/application-local.yml`。

默认连接：

```text
Host: 127.0.0.1
Port: 13306
Database: playmate_space
Username: playmate
Password: playmate_dev_password
```

后续不要把正式环境密码写入配置文件，使用环境变量覆盖：

```text
PLAYMATE_DB_HOST
PLAYMATE_DB_PORT
PLAYMATE_DB_NAME
PLAYMATE_DB_USERNAME
PLAYMATE_DB_PASSWORD
PLAYMATE_JWT_SECRET
```

## 启动后端

```bash
cd playmate-server
mvn spring-boot:run
```

如果本机 Maven 配置了不可用的内网 Nexus，可以临时走 Maven Central：

```bash
cd playmate-server
mvn -s ../docs/maven-central-settings.xml spring-boot:run
```

如果需要指定端口：

```bash
SERVER_PORT=8080 mvn spring-boot:run
```

## 验证健康检查

```bash
curl http://127.0.0.1:8080/api/health
```

预期返回统一格式：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "status": "UP",
    "database": "UP",
    "userCount": 0
  },
  "traceId": "..."
}
```

## 验证鉴权拦截

`/api/health` 和 `/api/auth/wx-login` 会放行。

其他 `/api/**` 接口默认需要：

```text
Authorization: Bearer <token>
```

JWT 只保存 `userId` 等必要身份信息，不保存活动权限。活动成员权限后续在 Service 层查询业务表判断。
