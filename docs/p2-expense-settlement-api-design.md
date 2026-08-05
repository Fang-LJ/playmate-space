# P2 费用与 AA 结算接口

所有接口均需要当前用户是活动 `ACTIVE` 成员。活动已结束仍允许补记费用和结算；活动已取消只读。

## 费用

- `GET /api/activities/{activityId}/expenses/summary`：当前用户费用摘要、本人建议转账和最近账单。
- `GET /api/activities/{activityId}/expenses?category=FOOD`：活动账单列表。
- `POST /api/activities/{activityId}/expenses`：新增账单。字段为 `title`、`category`、`amount`、`payerUserId`、`expenseTime`、`splitMode`、`shares`、可选 `receiptFileId`、`description`。
- `GET /api/activities/{activityId}/expenses/{expenseId}`：账单及分摊详情。
- `PUT /api/activities/{activityId}/expenses/{expenseId}`：编辑账单，必须传当前 `version`。
- `POST /api/activities/{activityId}/expenses/{expenseId}/void`：作废账单，保留历史但不再参与结算。
- `GET /api/activities/{activityId}/expenses/members`：成员付款、分摊、转账和剩余净额。

普通成员仅能作为自己的付款人；活动创建者可代记。付款人、分摊人和凭证上传人均在写入时校验活动有效成员和文件归属。`EQUAL` 分摊按分处理余数，`CUSTOM` 的分摊总和必须等于账单金额。

## 结算

- `GET /api/activities/{activityId}/settlements/summary`：全员余额、稳定贪心撮合出的最少建议和历史转账。
- `POST /api/activities/{activityId}/settlements/complete`：保存一笔真实线下转账。请求为 `fromUserId`、`toUserId`、`amount`、可选 `remark`，必须与当前建议完全匹配。
- `POST /api/activities/{activityId}/settlements/{settlementId}/cancel`：撤销已完成转账。付款操作人或活动创建者可撤销。
- `GET /api/activities/{activityId}/settlements/history`：查询完成/撤销的实际转账历史。

建议转账不持久化。账单、分摊或已完成转账变更后，下一次摘要查询会立即按事实数据重算。

## 文件

`POST /api/files/upload` 额外支持 `fileType=EXPENSE_RECEIPT`，仅接受 jpg/jpeg/png/webp，单文件不超过 5MB。
