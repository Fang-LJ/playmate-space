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

## P0 smoke test

后端启动后，可以在项目根目录执行 P0 全链路联调脚本：

```bash
bash scripts/p0-smoke-test.sh
```

脚本依赖：

- `curl`
- `jq`
- 可访问的后端服务：`http://127.0.0.1:8080`

脚本覆盖：

- health 接口
- Mock 登录
- 文件上传
- 活动创建、列表、详情
- 邀请信息、加入活动、重复加入
- 成员列表、修改活动内昵称、移除成员
- 被移除成员权限限制
- 已结束活动不可加入
- 已取消活动不可加入

脚本会使用带时间戳的 mockOpenid 和活动名称，不会清空数据库，不会删除 MinIO 文件，不会执行 Docker 停止或 volume 删除操作。运行结束会输出活动 ID、shareCode、测试用户 ID，方便排查。

## 验证鉴权拦截

`/api/health`、`/api/auth/wx-login`、`/api/auth/account-register` 和 `/api/auth/account-login` 会放行。

其他 `/api/**` 接口默认需要：

```text
Authorization: Bearer <token>
```

JWT 只保存 `userId` 等必要身份信息，不保存活动权限。活动成员权限后续在 Service 层查询业务表判断。

## P0.5 账号状态

登录接口响应包含 `isNewUser`、`accountProtected`、`profileComplete`、`showAccountProtectionNotice`：

- `accountProtected`：已设置密码，且手机号或邮箱至少有一个。
- `profileComplete`：昵称和头像都不为空。
- 两个状态只用于前端展示提示，客户端不得据此强制跳转账号保护页。
- 旧 `needCompleteProfile`、`needSetPassword` 仅为兼容字段，后续准备废弃。

本地 mock 微信身份固定为 `mock_user_a`、`mock_user_b`、`mock_user_c`；相同身份会通过 `t_user_identity` 命中同一个平台用户。

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

上传用户头像：

```bash
curl -X POST http://127.0.0.1:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "fileType=USER_AVATAR" \
  -F "file=@/path/to/avatar.png"
```

P0 上传规则：

- 接口路径：`POST /api/files/upload`
- 需要登录，不在拦截器放行列表中
- `fileType` 支持 `ACTIVITY_COVER` 和 `USER_AVATAR`
- 支持 `jpg`、`jpeg`、`png`、`webp`
- 单文件最大 `5MB`
- 上传成功后写入 `t_file`，并上传对象到 MinIO `playmate-files` bucket
- 本地 Docker 初始化会将 `playmate-files` 设置为可下载，方便小程序预览上传后的图片

## 验证当前用户资料

账号注册：

```bash
curl -X POST http://127.0.0.1:8080/api/auth/account-register \
  -H "Content-Type: application/json" \
  -d '{
    "account": "13800000001",
    "password": "123456",
    "nickname": "账号用户"
  }'
```

账号登录：

```bash
curl -X POST http://127.0.0.1:8080/api/auth/account-login \
  -H "Content-Type: application/json" \
  -d '{
    "account": "13800000001",
    "password": "123456"
  }'
```

微信登录后完善账号：

```bash
curl -X PUT http://127.0.0.1:8080/api/users/me/account \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "13800000002",
    "email": "local@example.com",
    "password": "123456"
  }'
```

查询当前用户：

```bash
curl http://127.0.0.1:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```

更新当前用户资料：

```bash
curl -X PUT http://127.0.0.1:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "nickname": "本地用户",
    "phone": "13800000000",
    "email": "user@example.com",
    "gender": "UNKNOWN",
    "address": "上海",
    "bio": "周末活动组织者",
    "avatarUrl": "http://127.0.0.1:19000/playmate-files/avatars/demo.png"
  }'
```

P0.5 账号和资料规则：

- 需要登录。
- 只能修改当前登录用户自己的资料。
- `t_user` 是平台用户账号，`t_user_identity` 保存微信身份。
- 微信登录不再依赖 `t_user.openid` 查询用户。
- 手机号 / 邮箱 + 密码可以注册登录。
- 密码使用 BCrypt，不存明文。
- 可修改资料字段：`nickname`、`avatarUrl`、`phone`、`email`、`gender`、`address`、`bio`。
- 不能修改 `openid`、`unionid`、`status`、`passwordHash`、`id`、`createTime`。
- 响应不返回 `openid`、`unionid`、`passwordHash`。

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

## 验证活动邀请和加入

查询邀请信息不强制登录，适合分享链接打开邀请页：

```bash
curl http://127.0.0.1:8080/api/activity-invites/{shareCode}
```

如果请求带有效 token，接口会额外返回当前用户是否已加入、是否可加入：

```bash
curl http://127.0.0.1:8080/api/activity-invites/{shareCode} \
  -H "Authorization: Bearer $TOKEN"
```

加入活动需要登录：

```bash
curl -X POST http://127.0.0.1:8080/api/activity-invites/{shareCode}/join \
  -H "Authorization: Bearer $TOKEN"
```

P0 邀请加入规则：

- 活动不存在或已逻辑删除：返回 HTTP 404，响应 code 为 `NOT_FOUND`
- 活动 `CANCELED`：返回邀请信息但 `canJoin=false`，加入接口拒绝
- 活动 `ENDED`：第一版不可加入
- 无成员记录：新增 `ACTIVE` + `MEMBER` 成员记录，并更新 `member_count`
- 已有 `ACTIVE` 成员记录：不重复插入，直接返回已加入
- 已有 `REMOVED` 成员记录：禁止重新加入
- 邀请信息接口不返回完整成员列表，不返回 openid

## 验证成员管理

查询活动成员列表：

```bash
curl http://127.0.0.1:8080/api/activities/{activityId}/members \
  -H "Authorization: Bearer $TOKEN"
```

修改自己的活动内昵称：

```bash
curl -X PUT http://127.0.0.1:8080/api/activities/{activityId}/members/me/nickname \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"nickname":"活动内昵称"}'
```

创建者移除普通成员：

```bash
curl -X DELETE http://127.0.0.1:8080/api/activities/{activityId}/members/{memberId} \
  -H "Authorization: Bearer $TOKEN"
```

P0 成员规则：

- 成员列表需要登录，且当前用户必须是活动 `ACTIVE` 成员
- 成员列表默认只返回 `ACTIVE` 成员，不返回 openid，不返回被移除成员
- 昵称只更新 `t_activity_member.activity_nickname`，不修改 `t_user.nickname`
- 移除成员只允许 `ACTIVE` 创建者操作
- 只能移除普通成员，不能移除创建者，不能移除自己
- 移除时更新 `member_status=REMOVED`，不物理删除成员记录
- 真实从 `ACTIVE` 改成 `REMOVED` 时同步更新 `t_activity.member_count - 1`
- 被移除成员不能查看活动详情、成员列表、编辑/结束/取消活动，也不能通过分享链接重新加入
