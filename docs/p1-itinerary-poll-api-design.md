# P1 行程 × 投票协作 API 设计

## 范围

本阶段实现行程、投票、投票结果应用和活动详情协作摘要；不实现费用、照片、AA 或通用待办。

## API 清单

| API | 用途 |
| --- | --- |
| `GET /api/activities/{activityId}/itineraries` | 查询按日期和时间排序的行程列表；默认不返回已取消项，`includeCanceled=true` 用于“查看全部”。 |
| `GET /api/activities/{activityId}/itineraries/{itineraryId}` | 查询行程详情及历史关联投票。 |
| `POST /api/activities/{activityId}/itineraries` | 创建直接确认行程，或创建待决定行程并同时发起关联投票。 |
| `PUT /api/activities/{activityId}/itineraries/{itineraryId}` | 编辑行程。 |
| `POST /api/activities/{activityId}/itineraries/{itineraryId}/cancel` | 取消行程，保留历史。 |
| `POST /api/activities/{activityId}/itineraries/{itineraryId}/restore` | 将已取消行程恢复为已确认。 |
| `DELETE /api/activities/{activityId}/itineraries/{itineraryId}` | 物理删除行程；存在未完成关联投票时拒绝删除。 |
| `GET /api/activities/{activityId}/itineraries/{itineraryId}/polls` | 查询行程历史关联投票。 |
| `GET /api/activities/{activityId}/polls` | 查询投票列表，并触发过期投票幂等关闭。 |
| `POST /api/activities/{activityId}/polls` | 创建普通、修改行程或生成行程投票。 |
| `GET /api/activities/{activityId}/polls/{pollId}` | 查询投票、选项、票数、当前用户选择和结果状态。 |
| `PUT /api/activities/{activityId}/polls/{pollId}` | 修改投票标题、说明、截止时间或允许修改标记。 |
| `POST /api/activities/{activityId}/polls/{pollId}/votes` | 提交或替换当前用户选择。 |
| `POST /api/activities/{activityId}/polls/{pollId}/close` | 创建者或活动创建者主动结束投票。 |
| `POST /api/activities/{activityId}/polls/{pollId}/cancel` | 取消投票。 |
| `GET /api/activities/{activityId}/polls/{pollId}/result-preview?optionId={optionId}` | 服务端计算指定选项的结果应用预览，不修改数据。 |
| `POST /api/activities/{activityId}/polls/{pollId}/apply-result` | 人工确认并应用指定胜出选项。 |
| `GET /api/activities/{activityId}/collaboration-summary` | 返回活动详情默认 Tab、行程/投票摘要和动态待办。 |
| `GET /api/users/me/activity-todos` | 查询当前用户 `PENDING` 的持久化待办，供首页角标和待办中心使用。 |
| `POST /api/activities/{activityId}/reminders` | 活动创建者向全体有效成员发布人工提醒。 |
| `POST /api/activity-todos/{todoId}/ack` | 当前被分配成员确认人工提醒。 |
| `GET /api/activities/{activityId}/reminders/{todoId}/ack-status` | 活动创建者查看人工提醒确认情况。 |
| `GET /api/itineraries/type-metadata` | 返回固定顺序的六种行程类型、重点字段、通用字段和允许的决策类型。 |

## 行程类型策略

后端以 `ItineraryTypePolicy` 作为行程类型规则的单一事实来源。创建、编辑、投票范围推导、结果应用、摘要和元数据接口均复用同一份规则。

| 类型 | 重点字段 | 通用字段 | 可用决策 |
| --- | --- | --- | --- |
| `TRANSPORT` | `transportMode/departureName/destinationName` | `title/itineraryDate/startTime/endTime/description` | `TRANSPORT/ROUTE/TIME` |
| `MEAL` | `mealType/restaurantName` | `title/itineraryDate/startTime/endTime/address/description` | `RESTAURANT/TIME` |
| `LODGING` | `locationName/startTime/endTime` | `title/itineraryDate/address/description` | `PLACE/TIME` |
| `SIGHTSEEING` | `activityContent/locationName` | `title/itineraryDate/startTime/endTime/address/description` | `CONTENT/PLACE/TIME` |
| `ACTIVITY` | `activityContent/locationName` | `title/itineraryDate/startTime/endTime/address/description` | `CONTENT/PLACE/TIME` |
| `OTHER` | `locationName` | `title/itineraryDate/startTime/endTime/address/description` | `PLACE/TIME` |

- 创建时，其他类型字段为空字符串会归一化为 `null`，包含真实内容则返回参数错误；重点字段可以为空，以支持 `PENDING_DECISION`。
- 类型切换会清空全部类型重点字段和 `address`，保留标题、日期、时间和备注，再写入请求明确提供的新类型字段。同名的 `locationName` 也不会跨语义保留。
- `LODGING` 的 `endTime <= startTime` 表示次日离开；其他非全天行程仍要求结束时间晚于开始时间。
- `displaySummary` 按六种类型分别生成，不包含 `description`。
- `routeDetail` 进入兼容废弃状态：数据库列保留；新请求内容合并到 `description`，新投票不允许修改；历史记录在 `description` 为空时仍可展示历史路线内容。

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
    "decisionScope": ["mealType", "restaurantName", "address"],
    "allowModify": true,
    "options": [
      {"optionText": "海底捞", "resultPayload": {"mealType": "火锅", "restaurantName": "海底捞湖滨店", "address": "湖滨路 88 号"}},
      {"optionText": "烧烤", "resultPayload": {"mealType": "烧烤", "restaurantName": "湖滨烧烤店", "address": "湖滨路 18 号"}}
    ]
  }
}
```

## 权限与活动状态

- ACTIVE 成员可读、创建行程和投票、参与投票。
- 普通成员仅可编辑、取消、恢复或删除自己创建的行程和投票；活动创建者可管理全部。
- `PLANNING`、`ONGOING` 可写；`ENDED`、`CANCELED` 全部行程与投票接口只读。
- 非成员访问任何 P1 数据均返回 `FORBIDDEN`。
- 已取消行程默认不出现在活动详情摘要；“查看全部行程”会携带 `includeCanceled=true`，并允许有管理权限的用户恢复。删除为不可恢复的物理删除；未完成关联投票会阻止删除，已完成或已取消投票会先解除行程关联再删除。

## 投票规则

- `GENERAL` 支持单选/多选，不应用行程。
- `UPDATE_ITINERARY`、`CREATE_ITINERARY` 必须单选。
- `UPDATE_ITINERARY` 根据目标行程类型推导最大 `decisionScope`；`CREATE_ITINERARY` 根据模板中的 `itineraryType` 推导。前端可以不传，也只能传后端范围的非空子集，不能扩大范围。
- 决策范围固定为：交通方式 `transportMode`；交通路线 `departureName/destinationName`；时间 `itineraryDate/startTime/endTime`；餐厅 `mealType/restaurantName/address`；地点 `locationName/address`；活动内容 `activityContent`。不适用于目标类型的组合直接拒绝。
- 每个选项的 `resultPayload` 必须是 `decisionScope` 的子集；未授权字段在创建投票时直接返回参数错误。
- 新关联投票不能使用 `ITINERARY_NAME/OTHER` 修改稳定标题，`title` 固定来自已有行程或 `itineraryTemplate`。
- 单选限制、截止校验、选择替换均在事务中执行。
- 过期投票会在列表、详情和投票操作前幂等关闭。

## 结果应用与人工确认

- 唯一胜出、有效结果负载、活动可写且行程版本一致时自动应用。
- 预览与正式应用使用同一套字段映射和白名单；正式应用不信任前端预览内容。
- 应用成功后写入 `t_activity_poll_application`，详情响应返回 `applicationHistory`，包含前后快照、变化字段、未变化字段、操作人和应用时间。
- 更新已有行程会确认状态并递增 `version`；生成行程会写入 `origin_type=POLL_RESULT`、`origin_poll_id` 和 `generated_itinerary_id`。
- 预览和正式应用后都会再次执行目标类型的完整字段与时间校验，非法组合不会写入行程。
- 无人投票、并列、版本冲突、结果负载为空，或普通成员试图自动覆盖他人行程时，设置 `REVIEW_REQUIRED`。
- 历史 `decisionScope` 含 `title` 或 `routeDetail` 的投票仍可读取和预览，但不会自动覆盖行程，会进入 `REVIEW_REQUIRED`，由有权限用户明确确认。
- 人工确认只允许投票创建者、目标行程创建者或活动创建者；覆盖他人行程仍须由行程创建者或活动创建者确认。

## 持久化待办

`GET /api/users/me/activity-todos` 仅读取 `t_activity_todo` 与 `t_activity_todo_user`，仅返回当前用户 `PENDING` 的真实待办：`todoId/activityId/activityName/todoType/title/content/actionType/sourceType/sourceId/dueTime/userStatus`。为保持现有小程序兼容，响应仍包含 `targetType/targetId/description/dueAt/actionText` 别名。

投票创建、成员加入、投票、关闭、取消、过期和结果应用在业务事务内同步驱动待办状态；查询待办不扫描投票、投票记录或行程，也不会触发投票状态变更。行程当前/下一条只由协作摘要返回，不计入 `todoCount`。

人工提醒使用相同待办生命周期：创建者可发布给全体有效成员；成员通过 ack 接口独立完成，创建者可查询已确认与未确认成员。

## 事务与错误

- 创建待决定行程 + 创建投票、替换投票选择、关闭投票 + 应用结果均使用事务。
- 参数错误使用 `PARAM_ERROR`；非成员或只读活动使用 `FORBIDDEN`；活动、行程、投票或选项不存在使用 `NOT_FOUND`。
- 不实现费用、照片、AA、复杂多轮/排名投票或一次投票生成多条行程。

## 当前阶段边界

本轮仅完成阶段 1 的后端类型策略与模型对齐。阶段 2 的行程页面 Figma 重构和阶段 3 的投票页面重构尚未执行；小程序仅做清除旧类型字段、住宿跨午夜校验和移除新路线投票 `routeDetail` 的必要兼容调整。
