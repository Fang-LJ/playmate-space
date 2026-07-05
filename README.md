# 玩伴空间

玩伴空间是一个面向朋友出行、聚会和多人活动的小程序项目，第一版重点覆盖活动规划、分享加入和成员管理等核心链路。

## 当前阶段

当前已完成 P0-001 到 P0-025：后端基础工程、Mock 登录、当前用户接口、小程序基础工程、小程序登录和我的页、MinIO 文件上传、活动创建、我的活动列表、活动详情、编辑/结束/取消活动、活动邀请信息、加入活动接口、小程序分享链接、邀请页、加入流程、成员列表、活动内昵称、移除成员和成员权限补强。

P0-026 smoke test 已完成，当前进入 P0 UI 对齐与体验收尾阶段。下一步聚焦页面 UI 对齐 Figma、去除测试痕迹、完成人工验收，不新增 P1 行程、投票、账本、AA、照片墙功能。

## 技术栈

- Java 21
- Spring Boot 3
- Maven
- MyBatis-Plus
- MySQL 8
- MinIO
- 微信小程序

## 目录结构

```text
playmate-space/
├── playmate-server        # Spring Boot 后端服务
├── playmate-miniprogram   # 微信小程序前端
├── docs                   # 项目文档、SQL、开发说明
├── deploy                 # Docker Compose、本地环境配置
└── README.md              # 项目说明
```

## 本地 Docker 环境

复制本地环境变量示例：

```bash
cp deploy/.env.example deploy/.env
```

启动 MySQL 和 MinIO：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
```

Redis 是可选服务，如需启动再增加 `--profile optional`。

停止本地 Docker 环境：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml down
```

## 后端启动

```bash
cd playmate-server
mvn -s ../docs/maven-central-settings.xml spring-boot:run
```

默认使用 `local` profile。也可以通过环境变量指定：

```bash
SPRING_PROFILES_ACTIVE=local mvn -s ../docs/maven-central-settings.xml spring-boot:run
```

## 健康检查

```text
GET /api/health
```

本地访问：

```bash
curl http://127.0.0.1:8080/api/health
```

## P0 全链路联调

后端启动后，可以执行本地 smoke test 脚本自动验证 P0 核心后端链路：

```bash
bash scripts/p0-smoke-test.sh
```

脚本会自动完成：

- health 检查
- Mock 用户 A/B/C 登录
- 活动封面上传
- 创建活动、列表、详情
- 邀请信息和加入活动
- 重复加入不重复增员
- 成员列表、活动内昵称、移除成员
- 被移除成员禁止访问和重新加入
- 已结束 / 已取消活动禁止新成员加入

脚本要求本机已有 `curl` 和 `jq`，并且后端可通过 `http://127.0.0.1:8080` 访问。脚本不会清空数据库，不会删除 MinIO 文件，也不会停止 Docker 容器。

## P0 UI 对齐计划

P0 功能闭环完成后，UI 对齐和体验收尾方案记录在：

```text
docs/p0-ui-polish-plan.md
```

本阶段只做 P0 已有页面的 Figma 对齐、正式文案和体验优化，不新增行程、投票、账本、AA、照片墙等 P1-P4 功能。

## 当前已完成

- P0-001 创建项目目录和 Git 仓库
- P0-002 编写 P0 初始化 SQL
- P0-003 配置本地 MySQL 和 MinIO
- P0-004 初始化 Spring Boot 后端项目
- P0-005 接入 MySQL、MyBatis-Plus、实体和 Mapper
- P0-006 实现统一返回、异常处理、参数校验
- P0-007 实现 JWT 鉴权和登录拦截器
- P0-008 初始化微信小程序项目
- P0-009 引入 TDesign、配置 TabBar 和基础页面
- P0-010 封装请求工具和 token 存储
- P0-011 实现登录接口和 Mock 登录
- P0-012 实现当前用户接口
- P0-013 小程序接入登录和我的页
- P0-014 实现 MinIO 文件上传接口
- P0-015 小程序接入图片上传
- P0-016 实现活动创建接口
- P0-017 实现我的活动列表和活动详情接口
- P0-018 实现编辑、结束、取消活动接口
- P0-019 小程序实现活动列表、创建、详情页面
- P0-020 实现活动邀请信息接口
- P0-021 实现加入活动接口
- P0-022 小程序实现分享链接、邀请页、加入流程
- P0-023 实现成员列表、修改昵称、移除成员接口
- P0-024 小程序实现成员列表、改昵称、移除成员
- P0-025 实现成员权限校验补强
- P0-026 P0 全链路联调脚本

## 下一步

- P0 页面 UI 对齐 Figma
- 去除剩余测试痕迹
- 在微信开发者工具完成人工验收

## 文件上传验证

后端启动并登录获取 token 后，可以用 curl 验证活动封面上传：

```bash
curl -X POST http://127.0.0.1:8080/api/files/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F "fileType=ACTIVITY_COVER" \
  -F "file=@/path/to/test.png"
```

返回成功后会写入 `t_file`，并上传对象到本地 MinIO 的 `playmate-files` bucket。本地 Docker 初始化会将该 bucket 设置为可下载，方便小程序预览上传后的图片。

## 活动接口验证

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
  -d '{"description":"更新后的活动描述"}'
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

## 邀请加入验证

活动详情会返回 `shareCode`。未登录用户也可以查看有限邀请信息：

```bash
curl http://127.0.0.1:8080/api/activity-invites/{shareCode}
```

登录后加入活动：

```bash
curl -X POST http://127.0.0.1:8080/api/activity-invites/{shareCode}/join \
  -H "Authorization: Bearer $TOKEN"
```

P0 规则：

- 活动不存在返回 HTTP 404 和统一响应格式。
- 已取消活动不可加入。
- 已结束活动第一版不可加入。
- 已加入成员重复加入不会重复插入成员关系。
- 已被移除成员不可重新加入。
- 加入成功后写入 `t_activity_member`，并更新 `t_activity.member_count`。

## 成员管理验证

查询成员列表：

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

P0 规则：

- 成员列表只返回 `ACTIVE` 成员，不返回 openid。
- 普通成员只能修改自己的活动内昵称。
- 创建者可以移除普通成员，不能移除自己或创建者。
- 移除成员不物理删除记录，只把成员状态改为 `REMOVED`。
- 被移除成员不能再查看活动详情、成员列表，也不能通过分享链接重新加入。

## 小程序本地调试说明

- 小程序本地 API 地址默认配置为 `http://127.0.0.1:8080`，只适合微信开发者工具模拟器在本机调试。
- 真机预览时，`playmate-miniprogram/utils/config.js` 中的 `apiBaseUrl` 需要改成 Mac 的局域网 IP，例如 `http://192.168.x.x:8080`。
- 微信开发者工具本地调试 HTTP 接口时，需要关闭合法域名校验。
- 使用 TDesign MiniProgram 后，需要在微信开发者工具里执行「工具 -> 构建 npm」。
