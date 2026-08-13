# P2 费用与 AA 结算接口

所有接口均要求当前用户是活动 `ACTIVE` 成员。已结束活动允许补记和核对费用；已取消活动只读。

## 第一版边界

第一版只负责：记账、分摊、统计实际付款和应承担、实时计算谁应给谁多少钱。结算净额固定为：

```text
netAmount = paidAmount - shareAmount
```

- `netAmount > 0`：应收。
- `netAmount < 0`：应付。
- `netAmount = 0`：无需结算。
- 转账建议不持久化，不追踪线下转账是否真正发生。
- 账单继续使用 `ACTIVE / VOID`；作废不是物理删除。

## 第一版费用接口

- `GET /api/activities/{activityId}/expenses/summary`：活动详情费用 Tab 的轻量摘要。返回 `myNetAmount`、`mySettlementText`、当前用户直接相关的 `mySuggestions`、`suggestionCount`、`expenseCount`、最近两笔有效账单和 `financeVersion`。
- `GET /api/activities/{activityId}/expenses/dashboard`：独立费用详情聚合接口，一次计算返回总额/账单数/参与人数、成员付款/承担/净额、全员建议和 `financeVersion`。
- `GET /api/activities/{activityId}/expenses?category=FOOD`：有效账单列表，只返回 `status=ACTIVE`，支持分类筛选和分页。
- `POST /api/activities/{activityId}/expenses`：新增账单。字段为 `title`、`category`、`amount`、`payerUserId`、`expenseTime`、`splitMode`、`shares`、必填 `clientRequestId`，以及可选 `receiptFileId`、`description`。
- `GET /api/activities/{activityId}/expenses/{expenseId}`：账单及分摊详情。
- `PUT /api/activities/{activityId}/expenses/{expenseId}`：编辑账单，必须传当前 `version`。数据库使用 `WHERE id/activity_id/version/status/delete_flag` 原子更新；冲突返回“账单已被其他成员修改，请刷新后重试”。
- `POST /api/activities/{activityId}/expenses/{expenseId}/void`：传入 `expectedVersion` 和可选 `reason`，使用数据库条件更新将账单置为 `VOID`；旧版本请求被拒绝。
- `GET /api/activities/{activityId}/expenses/members`：旧成员明细兼容接口。第一版新页面使用 dashboard 中的干净成员 DTO。

普通成员只能记录自己付款；活动创建者可以代活动成员记账。付款人、分摊人和凭证上传人均在写入时校验成员状态及文件归属。`EQUAL` 按分计算尾差并按成员 ID 稳定分配；`CUSTOM` 分摊总和必须严格等于账单金额。

## 财务一致性

- `financeVersion`：活动维度的费用事实版本，由 `t_activity_finance_state` 保存。
- `expense.version`：单笔账单版本，继续用于防止旧页面覆盖新数据。
- `clientRequestId`：新增账单请求幂等键；同一活动、创建人和请求 ID 永远代表第一次成功创建的账单。
- 写事务统一执行：基础权限校验 → `INSERT ... ON DUPLICATE KEY UPDATE` 懒初始化 → `SELECT ... FOR UPDATE` 锁活动财务状态 → 业务校验与账单/分摊写入 → `finance_version + 1` → 提交。
- 新增幂等重试、失败或回滚、无变化编辑、版本冲突不会递增 `financeVersion`。查询不存在的状态行时返回 `0`，不写数据库。
- 唯一索引为 `uk_expense_create_request(activity_id, created_by, client_request_id)`；旧数据的请求 ID 可以为 `NULL`。

## Dashboard 响应

```json
{
  "summary": {
    "totalExpenseAmount": "558.00",
    "expenseCount": 2,
    "participantCount": 3
  },
  "members": [
    {
      "userId": 1,
      "nickname": "微信用户A",
      "avatarUrl": null,
      "paidAmount": "40.00",
      "shareAmount": "232.67",
      "netAmount": "-192.67",
      "settlementText": "应付 ¥192.67"
    }
  ],
  "suggestions": [
    {
      "fromUserId": 1,
      "fromNickname": "微信用户A",
      "toUserId": 3,
      "toNickname": "微信用户C",
      "amount": "192.67"
    }
  ],
  "calculationRule": "结算净额 = 实际付款 - 应承担",
  "financeVersion": 12
}
```

费用详情初始只并发请求 dashboard 与账单列表；切换成员/结算 Tab 不重新计算，切换账单分类只刷新账单列表。

## 第二版兼容接口

以下接口及 `t_activity_settlement` 历史数据暂时保留，供后续微信转账/真实转账状态使用，但小程序第一版没有入口，且这些记录不参与第一版 `netAmount`：

- `GET /api/activities/{activityId}/settlements/summary`
- `POST /api/activities/{activityId}/settlements/complete`
- `POST /api/activities/{activityId}/settlements/{settlementId}/cancel`
- `GET /api/activities/{activityId}/settlements/history`

## 文件

`POST /api/files/upload` 支持 `fileType=EXPENSE_RECEIPT`，仅接受 jpg/jpeg/png/webp，单文件不超过 5MB。

## 后续 TODO（未实现）

- 基于 `activityId + financeVersion` 的 Redis `SettlementSnapshot`。
- 缓存并发回填、失效与 Redis 故障回源 MySQL。

本阶段未引入 Redis dependency、`RedisTemplate`、`@Cacheable` 或任何缓存连接。
