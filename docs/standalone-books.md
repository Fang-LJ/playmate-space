# 独立算账账本

小程序底部导航为「活动 / 算账 / 我的」。在「算账」发起账本时，仅填写名称和可选的朋友昵称；创建者自动加入。手动成员无需注册即可付款和参与分摊。活动费用保持原入口。

## 操作流程

1. 创建账本，进入「成员」可继续添加参与人、修改昵称、停用或恢复成员。
2. 点击「记一笔」，填写消费、金额、付款人及参与人。支持均摊、自定义金额、比例分摊、付款凭证及备注；共用已有费用编辑和详情页面。
3. 在「怎么算」查看合并后的转账建议，或复制文本自行发送。系统不执行微信转账，也不记录实际转账状态。
4. 创建者可在「成员」发送通用邀请或「邀请本人关联」。通用邀请可选择新加入或关联已有手动成员。关联申请必须由创建者确认；申请人确认前没有账本访问权限，确认后保持原 memberId 和历史分摊。申请结果支持刷新，拒绝后可以重新选择。
5. 创建者归档后账本只读，仍可查看/复制结果；重新打开后可以继续修改。归档不代表结清。

## 权限与数据规则

- 只有有效成员能查看账本。所有写操作验证当前账号状态，并先锁定账本行；写事务使用 READ_COMMITTED，确保等到锁后读到最新成员和消费状态。
- 创建者可代所有有效成员记账；普通成员只能记录自己付款的消费。消费只能由记录人或创建者编辑、作废。
- 账目使用独立的 memberId；userId 仅用于登录身份及成员绑定，不创建假用户/假活动。
- 新成员加入不修改历史分摊。停用保留历史账目，但禁止该成员访问、重新加入以及参与新消费；如需修改包含停用成员的旧消费，先由创建者恢复成员。
- 每个账本最多 50 位成员（含停用成员）。账本列表每页 20 条，消费列表每页 30 条，支持触底加载和消费分类。
- 同一个账号在同一本账本中最多关联一个成员。多个账号同时认领同一个成员，只有一个审批能成功，其他申请标记拒绝。
- 单笔消费 version 防止过期编辑/作废；账本 version 防止旧页面改名或归档覆盖新变化。创建账本及新增消费使用 clientRequestId 幂等键，重试返回最初成功的数据。
- 金额分配和净额结算使用公共 ExpenseCalculator。均摊尾差按成员 ID；比例分摊按精确余数降序、成员 ID 升序分配，金额之和严格等于消费总额。
- dashboard 在 REPEATABLE_READ 事务中读取一致的成员、消费和分摊。新增/作废/编辑会更新账本版本。
- 公共邀请预览只返回账本名称、创建者昵称、可关联的手动成员昵称和 ID，不包含消费金额、真实账号 ID 或凭证。

## 接口

所有 `/api/books` 接口均需登录；仅邀请预览允许匿名访问。

| 方法 | 路径 | 用途 |
|---|---|---|
| GET / POST | `/api/books` | 分页列出本人账本 / 创建 |
| GET / PUT | `/api/books/{id}` | dashboard / 改名 |
| POST | `/api/books/{id}/state` | OPEN / ARCHIVED，携带 version |
| POST | `/api/books/{id}/members` | 添加手动成员 |
| PUT | `/api/books/{id}/members/{memberId}` | 修改账本内昵称 |
| POST | `/api/books/{id}/members/{memberId}/state` | ACTIVE / INACTIVE |
| GET | `/api/book-invites/{code}` | 公开邀请预览 |
| POST | `/api/books/join` | `{code, memberId?}`，直接加入或申请关联 |
| GET | `/api/books/join-status?code=...` | 只读查询本人加入/申请状态 |
| POST | `/api/books/{id}/claims/{claimId}` | `{approve: true/false}` 审批 |
| GET / POST | `/api/books/{id}/expenses` | 分页消费列表 / 记一笔 |
| GET / PUT | `/api/books/{id}/expenses/{expenseId}` | 详情 / 携带 version 编辑 |
| POST | `/api/books/{id}/expenses/{expenseId}/void` | 携带 expectedVersion 作废 |

请求 DTO 在 `BookRequests.java`；数据库访问位于 `BookRepository`；领域规则在 `BookService`。前端 `utils/expense-context.js` 将活动与账本映射到公共表单，其历史视图键 userId 在账本模式表示成员 ID，仅在适配器内使用；账本 API 明确使用 payerMemberId/memberId。

## 本地运行

已有数据库需要执行迁移（只新增五张独立表，不修改活动表）。本次开发已在本机 Docker 数据库执行：

```bash
docker exec -i playmate-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"' < docs/sql/p4_001_standalone_books.sql
mvn -f playmate-server/pom.xml package
java -jar playmate-server/target/playmate-server-0.0.1-SNAPSHOT.jar
```

已有后端进程需要重启加载新代码。小程序默认访问 `http://127.0.0.1:8080`，在微信开发者工具重新编译项目即可看到「算账」。全新 Docker 数据目录已通过 compose 挂载本迁移；已有 volume 不会自动重跑初始化脚本。

## 验证

```bash
mvn -f playmate-server/pom.xml test
node --test playmate-miniprogram/tests/*.test.js
# HTTP 冒烟测试需要本地 mock 登录；使用独立端口可避开当前开发进程。
PLAYMATE_API_BASE_URL=http://127.0.0.1:18080 python3 scripts/book-smoke-test.py
PLAYMATE_API_BASE_URL=http://127.0.0.1:18080 bash scripts/p2-expense-settlement-smoke-test.sh
```

HTTP 测试会创建带唯一测试前缀的账号和账本，并将测试账本归档，不修改已有账本。覆盖多人分摊、尾差、负数和非法请求、并发幂等、越权、跨账本引用、并发认领、成员停用/恢复、新成员不改旧分摊、并发编辑、归档只读及分页。

前端自动测试覆盖共享表单适配、历史分摊保留、重复保存拦截、归档表单禁用、认领申请刷新/拒绝、失效指定成员邀请、登录返回目标、列表请求竞态及账号/成员身份区分。

微信开发者工具自带 WXML/WXSS 编译器验证了新增页面与复用页面。当前 IDE 服务端口关闭，无法执行模拟器自动点击和截图；真实微信好友分享接收、双账号认领和凭证拍照仍需在开发者工具/真机验收。

### 本次验证结果（2026-09-08）

- Maven 全量测试：94 项，0 失败、0 错误、0 跳过；JAR 构建成功。
- Node 前端测试：10 项通过。
- 新账本 HTTP/MySQL 集成测试：13 组场景通过（含加入申请只读状态查询）。
- 原有活动 P2 费用接口冒烟回归通过，覆盖创建、编辑、作废、幂等、并发版本控制。
- 微信原生编译器：7 个 WXML 模板、9 个 WXSS 文件通过；所有小程序源 JavaScript 语法检查通过。
- 临时 18080 测试后端已关闭；原 8080 开发进程未重启，需要重启后加载新接口。
