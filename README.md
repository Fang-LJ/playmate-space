# 玩伴空间

玩伴空间是一个面向朋友出行、聚会和多人活动的小程序项目，第一版重点覆盖活动规划、分享加入和成员管理等核心链路。

## 当前阶段

当前处于 P0 后端基础工程阶段，已经完成基础目录、初始化 SQL、本地 Docker 环境、Spring Boot 后端工程、MyBatis-Plus 接入、统一返回、异常处理、参数校验、JWT 工具和登录拦截器。

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

启动 MySQL、MinIO 和 Redis：

```bash
docker compose --env-file deploy/.env -f deploy/docker-compose.local.yml up -d
```

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

## 当前已完成

- P0-001 创建项目目录和 Git 仓库
- P0-002 编写 P0 初始化 SQL
- P0-003 配置本地 MySQL 和 MinIO
- P0-004 初始化 Spring Boot 后端项目
- P0-005 接入 MySQL、MyBatis-Plus、实体和 Mapper
- P0-006 实现统一返回、异常处理、参数校验
- P0-007 实现 JWT 鉴权和登录拦截器

## 下一步

- P0-011 登录接口和 Mock 登录
