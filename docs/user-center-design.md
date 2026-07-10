# 用户中心与登录注册方案

本文档记录 P0.5 阶段账号体系、登录注册和个人资料边界。当前阶段只做用户中心收尾，不新增行程、投票、账本、AA、照片墙。

## 结论

- `t_user` 是平台用户账号。
- `t_user_identity` 存第三方授权身份，当前只接入微信小程序身份。
- 微信只是辅助注册和登录方式，不再把 `t_user.openid` 当作用户主身份。
- 用户也可以用手机号或邮箱 + 密码注册登录。
- 微信登录后不强制完善账号；账号保护以低干扰提示的方式由用户主动设置。
- P0.5 不做短信验证码，不做邮箱验证码，不做找回密码。
- P0.5 不接入真实微信 code2Session，继续保留 mock 微信登录能力，代码结构为真实微信登录预留。

## 账号模型

### t_user

`t_user` 表示平台用户账号，核心字段包括：

- `id`
- `nickname`
- `avatar_url`
- `phone`
- `email`
- `password_hash`
- `password_set`
- `gender`
- `address`
- `bio`
- `profile_completed`
- `status`
- `last_login_time`

`openid` 和 `unionid` 暂时保留为历史兼容字段，后续业务逻辑不依赖它们查询用户。

### t_user_identity

`t_user_identity` 表示登录身份绑定关系，当前用于保存微信小程序授权身份：

- `user_id`
- `identity_type`：当前为 `WECHAT_MINIPROGRAM`
- `identifier`：微信 openid
- `unionid`
- `appid`
- `auth_nickname`
- `auth_avatar_url`
- `raw_profile_json`
- `last_login_time`
- `bind_time`

唯一索引：`identity_type + identifier`。

## 微信登录流程

生产方向仍然是小程序 `wx.login` 获取 code 后，由后端调用微信 code2Session 换取 openid。

当前 P0.5 不接入真实 code2Session，继续使用 mock 登录链路：

1. 小程序读取固定模拟身份 `mock_user_a`、`mock_user_b` 或 `mock_user_c`，默认 A。
2. 调用 `POST /api/auth/wx-login`。
3. 后端通过 `WechatLoginService.resolveSession` 获取 openid / unionid。
4. 后端查询 `t_user_identity.identity_type = WECHAT_MINIPROGRAM` 且 `identifier = openid`。
5. 身份存在时，通过 `user_id` 查询 `t_user` 并返回 token。
6. 身份不存在时，先创建 `t_user`，再创建 `t_user_identity`。
7. 返回 `accountProtected`、`profileComplete`、`showAccountProtectionNotice`；小程序只用于展示提示，不据此强制跳转。

微信登录不会每次覆盖用户自己修改过的昵称和头像，只在平台资料为空时补齐。

微信首次注册且头像或昵称缺失时，小程序只展示一次可关闭的“完善微信资料”提示。选择“以后再说”后直接回到活动页或邀请页；选择“现在设置”才进入微信资料页。手机号/邮箱注册和密码登录不展示此提示。

## 手机号 / 邮箱密码登录

P0.5 新增：

- `POST /api/auth/account-register`
- `POST /api/auth/account-login`

规则：

- `account` 包含 `@` 时按邮箱处理，否则按手机号处理。
- 密码长度 6-64。
- 密码使用 BCrypt 存储，不存明文。
- `phone` 和 `email` 在数据库中建立唯一索引，MySQL 允许多个 `NULL`。
- P0.5 不校验手机号和邮箱真实性。

## 账号保护与资料补充

`pages/account-complete/index` 是用户主动进入的账号保护页，可从我的页提示或退出登录提醒进入。手机号/邮箱注册完成后已经具备账号保护，不会进入此页面。

可填写：

- 手机号
- 邮箱
- 密码

用户可以跳过，后续仍可在我的页继续设置。微信新用户的头像昵称补充使用可跳过的 `pages/wechat-profile/index` 页面。

账号保护：已设置密码，且手机号或邮箱至少有一个。

资料完整度：昵称和头像都已填写；不影响登录、分享或活动权限。

旧 `needCompleteProfile`、`needSetPassword` 仅作为兼容返回字段，后续准备废弃。

## 游客边界

P0.5 暂不做游客账号。未登录用户只允许：

- 查看首页未登录态。
- 查看活动邀请页有限信息。
- 进入登录页、注册页。

未登录用户不允许：

- 创建活动。
- 加入活动。
- 查看活动详情。
- 查看成员列表。
- 修改活动内昵称。
- 移除成员。
- 编辑个人资料。
- 上传头像或活动封面。

## 个人资料字段

P0.5 支持编辑：

- 昵称 `nickname`
- 头像 URL `avatarUrl`
- 手机号 `phone`
- 邮箱 `email`
- 性别 `gender`
- 地址 `address`
- 个人简介 `bio`

P0.5 不允许用户修改：

- `id`
- `status`
- `passwordHash`
- `openid`
- `unionid`
- `createTime`

`GET /api/users/me` 不返回 `openid`、`unionid`、`passwordHash`。

## 头像上传

P0.5 复用现有 `POST /api/files/upload`：

- 活动封面使用 `fileType=ACTIVITY_COVER`
- 用户头像使用 `fileType=USER_AVATAR`

头像文件仍写入 `t_file`，对象 key 使用 `avatars/{yyyyMMdd}/{userId}/{uuid}.{ext}`。个人资料保存时将返回的 `url` 写入 `t_user.avatar_url`。

微信资料页优先使用 `button open-type="chooseAvatar"` 获取临时头像文件，并上传到后端；开发者工具不支持时可用相册选择作为备用入口。昵称使用 `input type="nickname"`，允许用户自行填写。

微信手机号使用 `button open-type="getPhoneNumber"`，仅在用户点击后发起绑定。local profile 用 mock phone code 映射固定号码，不接真实微信服务端；真实环境的 `WechatPhoneService` 目前会明确提示未接入。

## 后续上线前补齐

- 真实微信 code2Session。
- 手机号短信验证码。
- 邮箱验证码。
- 找回密码。
- 账号合并和解绑。
- 第三方身份管理页。
