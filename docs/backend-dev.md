# 后端本地启动说明

后端工程使用 Java 21 + Spring Boot 3.x + Maven + MyBatis-Plus。

## 前置依赖

- Java 21
- Maven
- MySQL 本地 Docker 环境已启动

启动 MySQL 和 MinIO：

```bash
cp deploy/.env.example deploy/.env
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
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

## 验证文件上传

登录获取 token：

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:8080/api/auth/wx-login \
  -H 'Content-Type: application/json' \
  -d '{"mockOpenid":"mock_upload_001","nickname":"上传测试用户","avatarUrl":""}' \
  | jq -r '.data.token')
```

上传活动封面：

```bash
curl -X POST http://127.0.0.1:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "fileType=ACTIVITY_COVER" \
  -F "file=@/path/to/test.png"
```

P0 上传规则：

- 接口路径：`POST /api/files/upload`
- 需要登录，不在拦截器放行列表中
- `fileType` 只支持 `ACTIVITY_COVER`
- 支持 `jpg`、`jpeg`、`png`、`webp`
- 单文件最大 `5MB`
- 上传成功后写入 `t_file`，并上传对象到 MinIO `playmate-files` bucket
- 本地 Docker 初始化会将 `playmate-files` 设置为可下载，方便小程序预览上传后的图片

## 验证活动接口

创建活动：

```bash
curl -X POST http://127.0.0.1:8080/api/activities \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "周末杭州两日游",
    "type": "TRAVEL",
    "startDate": "2026-07-18",
    "endDate": "2026-07-19",
    "locationName": "杭州",
    "description": "周末短途旅行"
  }'
```

带活动封面创建时，先调用 `POST /api/files/upload` 获取 `fileId`，再在请求体中传入 `coverFileId`。

查询我的活动列表：

```bash
curl http://127.0.0.1:8080/api/activities \
  -H "Authorization: Bearer $TOKEN"
```

查询活动详情：

```bash
curl http://127.0.0.1:8080/api/activities/{activityId} \
  -H "Authorization: Bearer $TOKEN"
```

编辑活动：

```bash
curl -X PUT http://127.0.0.1:8080/api/activities/{activityId} \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "周末杭州轻旅行",
    "type": "TRAVEL",
    "startDate": "2026-07-18",
    "endDate": "2026-07-19",
    "locationName": "杭州",
    "description": "更新后的活动描述"
  }'
```

结束活动：

```bash
curl -X POST http://127.0.0.1:8080/api/activities/{activityId}/end \
  -H "Authorization: Bearer $TOKEN"
```

取消活动：

```bash
curl -X POST http://127.0.0.1:8080/api/activities/{activityId}/cancel \
  -H "Authorization: Bearer $TOKEN"
```

P0 活动接口规则：

- 创建活动默认状态为 `PLANNING`
- 创建活动会生成全局唯一 `shareCode`
- 创建活动会同步写入创建者成员记录，角色为 `CREATOR`
- 我的活动列表只返回当前用户 `ACTIVE` 成员关系下的活动
- 活动详情要求当前用户必须是活动 `ACTIVE` 成员
- `coverFileId` 只能使用当前登录用户上传的 `ACTIVITY_COVER` 文件
- 编辑、结束、取消活动只允许活动创建者操作
- `CANCELED` 活动不允许编辑
- `ENDED` 活动只允许修改封面和描述
- 取消活动只修改状态，不物理删除活动，不删除文件或 MinIO 对象
