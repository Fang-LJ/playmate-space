# 用户中心与登录注册方案

本文档记录 P0.5 阶段用户中心、登录注册和个人资料边界。当前阶段只做用户中心收尾，不新增行程、投票、账本、AA、照片墙。

## 结论

- 第一版主登录方式是微信一键登录。
- 本地开发继续保留 mock 登录，方便在没有真实微信配置时联调。
- 未登录用户可以查看活动邀请页的有限信息。
- 创建活动、加入活动、查看活动详情、成员操作、资料编辑必须登录。
- P0.5 不做真实短信验证码手机号登录。
- P0.5 不做真实游客账号。
- 手机号在 P0.5 只作为“联系电话 / 个人资料字段”，不作为登录凭证。
- 未来如果要支持微信、手机号、邮箱等多登录方式，再考虑新增 `t_user_identity` 账号身份表。

## 微信一键登录

生产方向仍然是小程序 `wx.login` 获取 code 后，由后端调用微信 code2Session 换取 openid。

当前 P0.5 不接入真实 code2Session，继续使用已有 mock 登录链路：

1. 小程序生成或读取本地 mockOpenid。
2. 调用 `POST /api/auth/wx-login`。
3. 后端根据 openid 创建或更新用户。
4. 后端返回 JWT token。
5. 小程序后续请求携带 `Authorization: Bearer <token>`。

## 游客边界

P0.5 暂不做游客账号。未登录用户只允许：

- 查看首页未登录态。
- 查看活动邀请页有限信息。
- 进入登录页。

未登录用户不允许：

- 创建活动。
- 加入活动。
- 查看活动详情。
- 查看成员列表。
- 修改活动内昵称。
- 移除成员。
- 编辑个人资料。
- 上传头像或活动封面。

## 手机号策略

`t_user.phone` 在 P0.5 只作为个人资料字段使用：

- 用户可以在编辑资料页填写联系电话。
- 后端只做长度校验。
- 不做短信验证码。
- 不做手机号唯一索引。
- 不把手机号作为登录凭证。

## 个人资料字段

P0.5 支持编辑：

- 昵称 `nickname`
- 头像 URL `avatarUrl`
- 联系电话 `phone`

P0.5 不允许用户修改：

- `openid`
- `unionid`
- `status`
- `id`
- `createTime`

## 头像上传

P0.5 复用现有 `POST /api/files/upload`：

- 活动封面使用 `fileType=ACTIVITY_COVER`
- 用户头像使用 `fileType=USER_AVATAR`

头像文件仍写入 `t_file`，对象 key 使用 `avatars/{yyyyMMdd}/{userId}/{uuid}.{ext}`。个人资料保存时将返回的 `url` 写入 `t_user.avatar_url`。

## 后续身份表规划

如果后续需要多登录方式，可以增加 `t_user_identity`：

- `user_id`
- `identity_type`：WECHAT / PHONE / EMAIL
- `identifier`：openid / phone / email
- `credential`：密码哈希或第三方凭证摘要，按登录方式决定
- `verified`
- `create_time`
- `update_time`
- `delete_flag`

当前 P0.5 不创建该表，避免过早重构账号体系。
