# P3 照片墙 Round 1 设计冻结

## 边界、权限与表关系

本轮仅冻结数据库模型、Java Entity/Mapper、枚举和 API/安全架构；不实现 Controller、PhotoService、点赞/举报/审核 Service、真实微信审核、私有 MinIO 或小程序 UI。

照片墙完全复用活动访问权限：能够访问活动的 `ACTIVE` 成员即可查看活动照片墙，不新增 photo ACL 或成员权限表。未来上传按活动成员写权限判断；仅上传者本人或活动创建者可删除。删除采用业务软删除：照片记为 `DELETED`，记录 `deleted_by/deleted_at`；文件记为 `DELETING`，对象存储删除留给后台任务。

```text
t_activity 1:N t_activity_photo 1:1 t_file
                    ├── 1:N t_activity_photo_like
                    ├── 1:N t_activity_photo_report
                    └── 1:N t_activity_photo_audit_task
```

继续使用逻辑外键和索引，不引入 MySQL FOREIGN KEY，以保留删除、举报、审核和点赞历史。

## 照片三维状态机

三个字段必须独立：

| 维度 | 状态 | 作用 |
| --- | --- | --- |
| `status` | `ACTIVE/DELETED` | 照片业务记录是否有效。 |
| `audit_status` | `PENDING/APPROVED/REJECTED` | 内容审核结论。 |
| `visibility_status` | `NORMAL/REVIEWING/HIDDEN` | 普通成员当前是否可展示。 |

初次上传为 `ACTIVE + PENDING + HIDDEN`。初审通过变为 `ACTIVE + APPROVED + NORMAL`；拒绝变为 `ACTIVE + REJECTED + HIDDEN`。举报不会抹掉初审结论：`APPROVED + NORMAL` 先变为 `APPROVED + REVIEWING`；二审通过恢复 `NORMAL`，二审拒绝才更新为 `REJECTED + HIDDEN`。上传者可查看自己的 PENDING、REJECTED、REVIEWING；其他成员仅能看到 `ACTIVE + APPROVED + NORMAL`，始终 fail closed。

## 文件生命周期、审核和举报

文件与内容状态不能复用：

```text
TEMP -> BOUND -> DELETING -> DELETED
```

`TEMP` 是已上传未绑定的私有 PHOTO 文件，设置 `expire_at` 供清理孤儿对象；绑定照片后为 `BOUND`，记录 `bound_at` 且清空 `expire_at`。审核失败照片仍可保持 `BOUND`，由后续清理策略决定是否真正删除对象。

初审事务只创建/绑定照片和 `INITIAL + PENDING` 的持久审核任务后提交。提交外部审核失败写 `RETRY_WAIT`、重试次数、下次时间和错误；后台扫描 `PENDING/RETRY_WAIT` 后重试。`t_activity_photo_audit_task` 因此就是 durable job：应用在事务提交后宕机也不会丢任务，不需要 RabbitMQ，也不会把外部网络调用放进数据库事务。

任何可访问成员举报后，照片 `NORMAL -> REVIEWING`，创建唯一举报和 `REPORT_RECHECK` 任务；二审通过将举报置 `DISMISSED` 并恢复 `NORMAL`，拒绝则举报为 `CONFIRMED` 且照片为 `REJECTED + HIDDEN`。该保守策略优先安全，恶意举报阈值/信誉/人工复核留给后续版本。

未来微信异步审核由 `WechatContentModerationAdapter` 适配；具体回调 URL、签名和第三方 HTTP 协议必须在 Round 2 实现时再对照官方文档，不能写死进业务 Service。

## 私有对象存储与图片层级

未来 PHOTO 上传为 `PRIVATE + TEMP`。业务层只能通过 `fileId -> FileService / FileStorageService` 获取临时授权 URL，不能拼接 MinIO URL。Round 2 将扩展存储接口为 `upload/delete/generatePresignedUrl`，并支持 original、preview、thumbnail；`storage_provider` 使 `MINIO` 与未来 `COS` 文件可同时存在，`CosFileStorageService` 可无缝实现同一抽象。

原图为 `object_key`，快速预览为 `preview_object_key`，网格缩略图为 `thumb_object_key`。照片墙加载 thumbnail，点击后用 preview，明确点击“查看原图”才返回短时效原图 presigned URL。历史 `url/thumb_url` 保持兼容公开文件。

Round 2 上传必须校验 magic bytes、实际图片解码、宽高、图片尺寸上限和文件大小，仅允许 JPG/JPEG/PNG/WEBP；不能只相信扩展名或 `MultipartFile.contentType`。解析出的宽高写入 `t_file`，GIF/HEIC 本版不支持。

## Round 2 API 契约

所有接口都实时校验活动访问权限。

| 接口 | 语义 |
| --- | --- |
| `GET /api/activities/{activityId}/photos/summary` | `photoCount`（正常展示）、`pendingAuditCount`（仅计数）、`myUploadCount`、`receivedLikeCount`。 |
| `GET /api/activities/{activityId}/photos?scope=ALL|MINE|LIKED&sort=LATEST|EARLIEST|MOST_LIKED&page=1&pageSize=30` | 默认 30、最大 60。ALL 仅正常展示；MINE 为本人所有未删除照片；LIKED 为本人有效点赞且当前正常展示。 |
| `GET /api/activities/{activityId}/photos/{photoId}` | 返回照片/上传人信息、preview/thumbnail、文件元数据、点赞、审核状态、`canDelete/canReport`；禁止读取他人的非正常照片。 |
| `POST /api/activities/{activityId}/photos` | `{ "fileIds": [101,102] }`，最多 9 张，验证本人 `PHOTO + PRIVATE + TEMP` 文件，绑定并返回 `photoId/fileId/auditStatus`。 |
| `DELETE /api/activities/{activityId}/photos/{photoId}` | 上传者或创建者软删除，并推进文件清理生命周期。 |
| `POST/DELETE /api/activities/{activityId}/photos/{photoId}/like` | 幂等点赞/取消点赞。 |
| `POST /api/activities/{activityId}/photos/{photoId}/reports` | `{ "reasonCode": "PRIVACY" }`；无自由文本，触发复审。 |
| `GET /api/activities/{activityId}/photos/{photoId}/original-url` | 再次鉴权后返回短时效 `{url, expiresAt}`，不返回永久原图 URL。 |

列表项至少包含 `photoId/thumbnailUrl/uploadedBy/uploaderNickname/likeCount/likedByMe/auditStatus/visibilityStatus/canDelete/createTime`。`LATEST` 按创建时间倒序、`EARLIEST` 正序、`MOST_LIKED` 按有效点赞数倒序，均以 ID 稳定排序。两列/三列是小程序本地偏好，使用 `wx.setStorageSync`，不传后端。

点赞关系以 `t_activity_photo_like` 为事实，同一 `photo_id + user_id` 一行，在 `ACTIVE ↔ CANCELED` 间切换；`like_count` 仅是冗余。Round 2 必须在同一事务原子切换关系和增减计数，保证幂等、并发不重复加、计数不小于零。

## Round 2 顺序

1. 支持私有 TEMP PHOTO 上传、真实性检测和 preview/thumbnail 生成。
2. 实现 PhotoService 的绑定、查看、列表、删除与权限。
3. 实现点赞、举报、审核任务提交/重试与微信回调适配器。
4. 实现 presigned URL、对象清理任务与小程序 UI。
