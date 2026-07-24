# P1 活动协作增强数据库设计

## 设计目标

P1 为活动空间补充行程、投票、费用管理和照片墙的数据基础。行程是最终落地安排；投票是形成或调整行程的协作决策过程。费用管理包含预算、账单、分摊和实际转账记录；照片墙复用已有文件元数据。

## 设计边界

- 本阶段已创建 9 张协作业务表，并新增两张具备明确生命周期的活动待办表；不新增泛化任务、点赞、评论、相册分类或多凭证附件表。
- 不保存可由时间实时推导的状态，例如行程的未开始、进行中、已完成、预算总额和实际总额。待办不是时间扫描结果，而是由投票、提醒等业务事件写入和关闭的处理记录。
- 不使用数据库触发器自动应用投票结果或自动计算 AA；这些属于阶段 C 的事务和业务规则。
- 所有金额使用 `DECIMAL(12,2)`，不使用 `FLOAT` 或 `DOUBLE`。
- P1 表延续 P0 规范：`BIGINT AUTO_INCREMENT` 主键、`create_time`、`update_time`、`delete_flag`、InnoDB、`utf8mb4_0900_ai_ci`。

## 关系总览

```text
t_activity 1:N t_activity_itinerary
t_activity 1:N t_activity_poll 1:N t_activity_poll_option 1:N t_activity_poll_vote
t_activity_itinerary 1:N t_activity_poll (target_itinerary_id)
t_activity_poll 0:1 t_activity_itinerary (generated_itinerary_id / origin_poll_id)
t_activity_poll 1:0..1 t_activity_poll_application
t_activity 1:N t_activity_budget_item
t_activity 1:N t_activity_expense 1:N t_activity_expense_share
t_activity 1:N t_activity_settlement
t_activity 1:N t_activity_photo
t_activity 1:N t_activity_todo 1:N t_activity_todo_user
t_file 1:N t_activity_expense / t_activity_photo
t_user 为创建人、付款人、分摊人、投票人、结算双方和上传人的逻辑来源
```

## 外键策略

P0 表没有物理外键，P1 延续“逻辑外键 + 索引”策略。这样可保持逻辑删除、历史账单和成员移除后的数据可追溯性，避免物理级联删除破坏活动历史。阶段 C 在 Service 事务中校验活动、成员、文件与关联记录的存在性和归属。

## 表设计

| 表 | 用途 | 关键字段与索引 |
| --- | --- | --- |
| `t_activity_itinerary` | 活动最终行程 | 稳定 `title` 与结构化执行字段分离：交通方式、出发地、目的地、用餐类型、餐厅、活动内容、地点和地址；`route_detail` 仅保留历史兼容，新内容归入 `description`。按活动日期时间、待决定状态、创建人和来源投票索引。 |
| `t_activity_poll` | 协作投票及结果应用状态 | `purpose`、关联行程、胜出选项、目标版本、JSON `itinerary_template` 和字段白名单 `decision_scope`。 |
| `t_activity_poll_option` | 投票候选项 | 选项文本、JSON `result_payload`、`sort_no`；按投票和排序索引。 |
| `t_activity_poll_vote` | 用户对选项的投票记录 | `poll_id`、`option_id`、`user_id`；唯一约束防止同一用户重复投同一选项。 |
| `t_activity_poll_application` | 投票结果应用历史 | 每个投票最多一条正式应用记录，保存前后快照、实际变化、保持不变字段、操作人和时间。 |
| `t_activity_budget_item` | 预算明细 | 类别、计算方式、单价、数量、最终预算金额、可选关联行程；预算总额动态汇总。 |
| `t_activity_expense` | 实际账单 | 金额、付款人、消费时间、主凭证文件、状态和版本；已参与结算的账单通过 `VOID` 作废。 |
| `t_activity_expense_share` | 每位成员最终承担金额 | `expense_id`、`user_id`、`share_amount`；支持均摊、部分成员和后续自定义金额分摊。 |
| `t_activity_settlement` | 实际线下转账状态 | 转出人、收款人、金额、状态、完成时间；待支付建议仍可根据账单动态计算。 |
| `t_activity_photo` | 活动照片墙关联 | `activity_id`、`file_id`、上传人、拍摄时间、说明和排序；照片二进制继续保存在对象存储。 |
| `t_activity_todo` | 待办任务生命周期 | `todo_type`、来源、幂等 `source_key`、截止时间、任务状态和创建人；自动投票待办按 `activity_id + source_key` 唯一。 |
| `t_activity_todo_user` | 每位成员的待办处理状态 | `todo_id`、`user_id`、待处理/完成/取消、完成时间和原因；按 `todo_id + user_id` 唯一。 |

## 行程与投票关联

- 普通投票：`purpose=GENERAL`，不关联行程。
- 修改已有行程：投票写入 `target_itinerary_id` 和发起时的 `target_itinerary_version`；关闭后根据 `winner_option_id`、`result_payload` 生成预览。版本不一致时，将 `result_apply_status` 置为 `REVIEW_REQUIRED`，由人工确认。
- `decision_scope` 是结果应用的持久化字段白名单。新投票由后端根据行程类型和决策类型推导，客户端只能缩小范围；选项 `result_payload` 只能包含最终范围字段，后端预览和正式应用均再次校验。
- 新关联投票不能把 `title` 或 `routeDetail` 放入 `decision_scope`。稳定标题来自已有行程或创建模板；历史包含标题或路线的投票继续可读，但自动应用会转入人工确认。
- 生成新行程：投票的 `purpose=CREATE_ITINERARY`，固定字段存入 JSON `itinerary_template`；结果应用后写入 `generated_itinerary_id`，新行程记录 `origin_type=POLL_RESULT` 和 `origin_poll_id`。
- 一个行程可被多次历史投票引用，因为多个投票可以使用同一个 `target_itinerary_id`；“同一行程同时只允许一个进行中修改投票”留给阶段 C 的事务校验，不用复杂生成列或触发器实现。
- 并列、无人投票、版本冲突均由 `winner_option_id`、`result_apply_status` 和人工确认流程承载，不在数据库自动决策。

## 费用与结算关系

- 预算总额为 `t_activity_budget_item.estimated_amount` 的动态汇总，不写回 `t_activity`。
- 每笔 `t_activity_expense` 有一个付款人，可有多条 `t_activity_expense_share`，保存最终分摊金额。
- AA 建议依据有效账单、付款人和分摊记录实时计算；`t_activity_settlement` 只持久化实际转账建议的状态变化，不代表账单快照。
- MySQL 8 支持 `CHECK`，但 P0 现有 SQL 未使用数据库检查约束；P1 保持一致，由阶段 C 校验金额大于零、转出人不等于收款人及活动成员归属。

## 关键枚举

- 行程：`TRANSPORT/MEAL/LODGING/SIGHTSEEING/ACTIVITY/OTHER`；规划状态 `DRAFT/PENDING_DECISION/CONFIRMED/CANCELED`。
- 投票：用途 `GENERAL/UPDATE_ITINERARY/CREATE_ITINERARY`；类型 `SINGLE/MULTIPLE`；状态 `DRAFT/ACTIVE/CLOSED/CANCELED`；应用状态 `NOT_REQUIRED/PENDING/APPLIED/REVIEW_REQUIRED/FAILED`。
- 费用分类：`TRANSPORT/LODGING/TICKET/FOOD/ENTERTAINMENT/SHOPPING/OTHER`。
- 预算计算方式：`UNIT_X_QUANTITY/DIRECT_AMOUNT`；预算状态 `ACTIVE/VOID`。
- 账单状态：`ACTIVE/VOID`；结算状态 `PENDING/COMPLETED/CANCELED`；照片状态 `ACTIVE/DELETED`。

## 行程类型字段矩阵

六种类型仍共用 `t_activity_itinerary`，不拆子表。后端策略决定允许非空的字段：

- `TRANSPORT`：`transport_mode/departure_name/destination_name`，不使用 `address`。
- `MEAL`：`meal_type/restaurant_name/address`。
- `LODGING`：`location_name/address`，`start_time/end_time` 表示入住和离开；结束时间早于或等于开始时间表示次日。
- `SIGHTSEEING`、`ACTIVITY`：`activity_content/location_name/address`。
- `OTHER`：`location_name/address`，不是允许任意字段的兜底类型。

类型切换由服务层清理旧类型字段。`route_detail` 不删除、不迁移历史数据，新客户端不再将其作为正式业务字段；读取时在 `description` 为空的情况下兼容展示。

## 约束与索引

- `uk_poll_vote_poll_option_user (poll_id, option_id, user_id)`：防止相同选项重复投票。
- `uk_poll_application_poll (poll_id)`：一个投票只保存一次正式结果应用，重复确认保持幂等。
- `uk_expense_share_expense_user (expense_id, user_id)`：一笔账单每个成员仅一条最终分摊。
- `uk_activity_photo_activity_file (activity_id, file_id)`：同一文件不能重复加入同一活动。
- 活动维度列表均有联合索引：行程按日期时间、投票按状态截止时间、账单按消费时间、结算按状态、照片按上传时间。

## 活动待办生命周期

- 业务表仍是事实来源：投票、结算、人工提醒决定是否产生待办；`t_activity_todo` 保存任务生命周期，`t_activity_todo_user` 保存每位成员的处理状态。
- 自动投票待办使用 `POLL_VOTE:{pollId}`、`POLL_RESULT_CONFIRM:{pollId}` 作为 `source_key`，重复创建、回填或重试不会重复生成任务。
- 投票创建时分配 `POLL_VOTE`；成员投票后仅完成自己的待办；投票关闭、取消或过期后关闭剩余待办。`REVIEW_REQUIRED` 时只给有确认权限的成员分配 `POLL_RESULT_CONFIRM`，结果应用后关闭。
- 人工提醒使用 `MANUAL_REMINDER`，每位成员独立确认，完成原因为 `ACKNOWLEDGED`。活动取消时取消全部未完成待办。
- 行程当前/即将开始仍在协作摘要中单独计算，不写入待办表，也不计入待办数。

## SQL 与执行方式

- 建表 SQL：[p1_001_activity_collaboration.sql](sql/p1_001_activity_collaboration.sql)、[p1_002_activity_todo.sql](sql/p1_002_activity_todo.sql)、[p1_003_itinerary_poll_field_linkage.sql](sql/p1_003_itinerary_poll_field_linkage.sql)。
- 新环境：Docker MySQL 初始化顺序增加 `004-p1_itinerary_poll_field_linkage.sql`。
- 已存在的本地开发库：执行 `p1_003_itinerary_poll_field_linkage.sql`。脚本检查列是否存在并使用 `CREATE TABLE IF NOT EXISTS`，可重复执行且不清空已有数据。
- 历史数据回填为显式、一次性操作：完成迁移后可设置 `PLAYMATE_TODO_BACKFILL_ON_STARTUP=true` 启动后端。它只处理进行中投票和 `REVIEW_REQUIRED` 结果，执行幂等。
- 行程类型策略调整不修改表结构，也不新增迁移 SQL；现有 `route_detail`、`all_day` 和历史投票 JSON 全部保留。

## 后续范围

- 账单分摊校验、AA 计算与账单变化后的结算刷新策略。
- 费用、照片、已结束或已取消活动的可编辑和只读规则。
