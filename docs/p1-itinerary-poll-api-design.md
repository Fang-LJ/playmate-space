# P1 行程 × 投票协作 API 设计

## 范围

本阶段实现行程、投票、投票结果应用和活动详情协作摘要；不实现费用、照片、AA 或通用待办。

## API 清单

| API | 用途 |
| --- | --- |
| `GET /api/activities/{activityId}/itineraries` | 查询按日期和时间排序的行程列表。 |
| `GET /api/activities/{activityId}/itineraries/{itineraryId}` | 查询行程详情及历史关联投票。 |
| `POST /api/activities/{activityId}/itineraries` | 创建直接确认行程，或创建待决定行程并同时发起关联投票。 |
| `PUT /api/activities/{activityId}/itineraries/{itineraryId}` | 编辑行程。 |
| `POST /api/activities/{activityId}/itineraries/{itineraryId}/cancel` | 取消行程，保留历史。 |
| `GET /api/activities/{activityId}/itineraries/{itineraryId}/polls` | 查询行程历史关联投票。 |
| `GET /api/activities/{activityId}/polls` | 查询投票列表，并触发过期投票幂等关闭。 |
| `POST /api/activities/{activityId}/polls` | 创建普通、修改行程或生成行程投票。 |
| `GET /api/activities/{activityId}/polls/{pollId}` | 查询投票、选项、票数、当前用户选择和结果状态。 |
| `PUT /api/activities/{activityId}/polls/{pollId}` | 修改投票标题、说明、截止时间或允许修改标记。 |
| `POST /api/activities/{activityId}/polls/{pollId}/votes` | 提交或替换当前用户选择。 |
| `POST /api/activities/{activityId}/polls/{pollId}/close` | 创建者或活动创建者主动结束投票。 |
| `POST /api/activities/{activityId}/polls/{pollId}/cancel` | 取消投票。 |
| `POST /api/activities/{activityId}/polls/{pollId}/apply-result` | 人工确认并应用指定胜出选项。 |
| `GET /api/activities/{activityId}/collaboration-summary` | 返回活动详情默认 Tab、行程/投票摘要和动态待办。 |
| `GET /api/users/me/activity-todos` | 聚合当前用户可访问活动中的动态待办，按活动分组供首页角标和待办中心使用。 |

## 核心请求示例

```json
{
  "creationMode": "WITH_POLL",
  "title": "周六晚上晚餐",
  "itineraryType": "MEAL",
  "itineraryDate": "2026-07-18",
  "startTime": "18:00:00",
  "endTime": "20:00:00",
  "allDay": false,
  "poll": {
    "title": "晚餐吃什么？",
    "purpose": "UPDATE_ITINERARY",
    "decisionType": "RESTAURANT",
    "voteType": "SINGLE",
    "allowModify": true,
    "options": [
      {"optionText": "火锅", "resultPayload": {"title": "火锅晚餐", "locationName": "湖滨火锅"}},
      {"optionText": "烧烤", "resultPayload": {"title": "烧烤晚餐"}}
    ]
  }
}
```

## 权限与活动状态

- ACTIVE 成员可读、创建行程和投票、参与投票。
- 普通成员仅可编辑/取消自己创建的行程和投票；活动创建者可管理全部。
- `PLANNING`、`ONGOING` 可写；`ENDED`、`CANCELED` 全部行程与投票接口只读。
- 非成员访问任何 P1 数据均返回 `FORBIDDEN`。

## 投票规则

- `GENERAL` 支持单选/多选，不应用行程。
- `UPDATE_ITINERARY`、`CREATE_ITINERARY` 必须单选。
- 单选限制、截止校验、选择替换均在事务中执行。
- 过期投票会在列表、详情和投票操作前幂等关闭。

## 结果应用与人工确认

- 唯一胜出、有效结果负载、活动可写且行程版本一致时自动应用。
- 更新已有行程会确认状态并递增 `version`；生成行程会写入 `origin_type=POLL_RESULT`、`origin_poll_id` 和 `generated_itinerary_id`。
- 无人投票、并列、版本冲突、结果负载为空，或普通成员试图自动覆盖他人行程时，设置 `REVIEW_REQUIRED`。
- 人工确认只允许投票创建者、目标行程创建者或活动创建者；覆盖他人行程仍须由行程创建者或活动创建者确认。

## 动态待办

活动详情摘要最多返回 3 条动态待办；`GET /api/users/me/activity-todos` 返回当前用户的完整动态待办清单，聚合未参与的进行中投票、即将截止投票、待确认投票结果、24 小时内开始的行程和正在进行的行程。不建通用待办表。

人工提醒不在本轮实现。后续如需创建者发布提醒并让成员确认，需单独设计 `t_activity_reminder`、`t_activity_reminder_ack`、发布流程和“我已知晓”状态，不使用前端本地缓存伪造跨设备状态。

## 事务与错误

- 创建待决定行程 + 创建投票、替换投票选择、关闭投票 + 应用结果均使用事务。
- 参数错误使用 `PARAM_ERROR`；非成员或只读活动使用 `FORBIDDEN`；活动、行程、投票或选项不存在使用 `NOT_FOUND`。
- 不实现费用、照片、AA、复杂多轮/排名投票或一次投票生成多条行程。
