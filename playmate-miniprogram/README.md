# 玩伴空间小程序

当前目录是「玩伴空间」微信小程序原生工程，使用 WXML / WXSS / JS 开发，并通过 TDesign MiniProgram 提供基础组件。

## 本地运行

1. 使用微信开发者工具打开 `playmate-miniprogram` 目录。
2. 如果提示 AppID，可先使用测试号或保留 `touristappid` 进行本地预览。
3. 安装依赖：

```bash
pnpm install
```

4. 在微信开发者工具中执行「工具」->「构建 npm」。
5. 编译运行后，应能看到底部 TabBar：
   - 活动
   - 我的

## 命令行基础检查

可以用 Node 对小程序 JS 做基础语法检查：

```bash
pnpm install --frozen-lockfile
node --check app.js
node --check utils/request.js
node --check utils/token.js
node --check services/activity.js
node --check services/file.js
node --check services/invite.js
node --check services/member.js
node --check services/user.js
node --check pages/account-login/index.js
node --check pages/account-register/index.js
node --check pages/account-complete/index.js
node --check pages/activity-list/index.js
node --check pages/activity-create/index.js
node --check pages/activity-detail/index.js
node --check pages/activity-edit/index.js
node --check pages/activity-invite/index.js
node --check pages/link-invalid/index.js
node --check pages/member-list/index.js
node --check pages/member-nickname-edit/index.js
node --check pages/profile-edit/index.js
node --check pages/login/index.js
```

命令行检查只能发现基础 JS / JSON 语法问题，不能完全替代微信开发者工具。涉及 `wx.uploadFile`、页面跳转、分享路径、TDesign 组件渲染和真机网络环境时，仍需要在微信开发者工具里构建 npm 后验证。

## 本地接口配置

本地 API 地址配置在 `utils/config.js`：

```js
apiBaseUrl: 'http://127.0.0.1:8080'
```

- `http://127.0.0.1:8080` 只适合微信开发者工具模拟器在本机调试。
- 真机预览时需要把 `apiBaseUrl` 改成 Mac 的局域网 IP，例如 `http://192.168.x.x:8080`。
- 开发者工具访问本地 HTTP 接口时，需要在本地调试环境关闭合法域名校验。
- 使用 TDesign MiniProgram 后，需要在微信开发者工具里执行「工具 -> 构建 npm」。

## 当前范围

已完成：

- 原生小程序基础结构
- TDesign MiniProgram 依赖接入
- 活动 / 我的 TabBar
- 活动、我的、登录基础页面骨架
- `utils/request.js` 统一请求工具
- `utils/token.js` token 存取工具
- 本地 mockOpenid 登录
- 我的页当前用户信息展示
- 活动列表
- 创建活动
- 活动详情
- 编辑活动
- 结束活动
- 取消活动
- 活动封面上传
- 活动分享
- 活动邀请页
- 加入活动流程
- 成员列表
- 活动内昵称修改
- 移除成员
- 个人资料编辑页
- 用户头像上传
- 手机号 / 邮箱账号注册
- 手机号 / 邮箱密码登录
- 可选账号保护与微信头像昵称补充
- P0 页面 UI 对齐第一轮：活动列表、创建/编辑活动、活动详情、活动邀请、登录、我的、成员和链接失效页

未实现：

- 行程、投票、账本、AA、照片墙

## P0 页面预览重点

本轮 UI 已按 `docs/p0-ui-polish-plan.md` 做第一轮 Figma 对齐：

- 活动列表页：浅绿背景、状态筛选、白色活动卡片、绿色创建按钮。
- 创建活动页：分组表单、类型 chip、封面占位卡、日期卡片。
- 活动详情页：活动空间首页、成员摘要、出发前 / 活动中后模块分组。
- 邀请页：独立邀请落地页，突出朋友邀请加入活动。
- 我的页 / 登录页：正式产品文案和统一卡片风格。
- 成员页：头像、角色标签、当前用户标签和危险移除按钮。

建议截图时覆盖：未登录、无活动、已有活动、创建活动、活动详情、邀请加入、成员列表、昵称编辑、链接失效。

## 登录联调

登录页会调用：

- `POST /api/auth/wx-login`
- `POST /api/auth/account-register`
- `POST /api/auth/account-login`
- `GET /api/users/me`
- `PUT /api/users/me/account`

本地开发阶段微信登录使用固定模拟身份：`mock_user_a`、`mock_user_b`、`mock_user_c`，默认用户 A。登录页底部可弱化切换身份；选择相同身份会回到同一个平台账号。登录成功后 token 会写入本地 storage，刷新小程序后「我的」页会继续通过 token 请求当前用户信息。

微信登录、手机号/邮箱注册、手机号/邮箱登录成功后都会优先回到原目标页面；不会因为头像昵称、手机号或密码缺失而强制进入账号保护页。`account-complete` 现为可选的“账号保护”页面，只有用户主动点击才会进入。

活动列表支持“输入分享码加入活动”。输入会自动清理空格并转为大写，再进入现有邀请预览页；未登录用户可以先预览，点击加入后会登录并回到原邀请页。

账号登录 / 注册页支持手机号或邮箱 + 密码。P0.5 不做短信验证码、邮箱验证码、找回密码和真实微信 code2Session。

## 个人资料编辑验证

1. 先完成登录。
2. 进入「我的」Tab。
3. 点击「编辑资料」。
4. 点击头像区域的「更换」，选择图片。
5. 小程序会通过 `wx.uploadFile` 调用 `POST /api/files/upload`，`formData.fileType` 为 `USER_AVATAR`。
6. 修改昵称、联系电话、邮箱、性别、地址和个人简介。
7. 点击「保存」，小程序会调用 `PUT /api/users/me`。
8. 保存成功后返回「我的」页，应展示新的头像、昵称、联系方式和资料状态。

说明：手机号 / 邮箱在 P0.5 可用于账号密码登录，但不做短信或邮箱验证码。

## 封面上传验证

活动封面上传已经接入创建活动页和编辑活动页：

1. 先完成登录。
2. 进入创建活动页或编辑活动页。
3. 点击封面区域选择图片。
4. 小程序会通过 `wx.uploadFile` 调用 `POST /api/files/upload`。
5. 请求 header 会携带 `Authorization: Bearer token`。
6. `formData.fileType` 固定为 `ACTIVITY_COVER`。
7. 上传成功后页面保存 `coverFileId`，并展示封面预览。

如果使用真机预览，需要把 `utils/config.js` 的 `apiBaseUrl` 改成 Mac 的局域网 IP，并确认手机和 Mac 在同一网络。

## 活动验证

1. 先完成登录。
2. 进入「活动」Tab。
3. 点击「创建」进入创建活动页。
4. 可选上传封面，填写活动名称、类型、日期等信息。
5. 提交后返回活动列表，列表会在 `onShow` 时刷新。
6. 点击活动卡片进入详情页。
7. 创建者在详情页可以点击「编辑活动」「结束活动」「取消活动」。
8. 已结束活动进入编辑页后，只能修改封面和描述。
9. 已取消活动不再展示编辑、结束、取消按钮。

## 分享邀请验证

1. 用户 A 登录并创建一个规划中的活动。
2. 进入活动详情页，点击「分享活动」。
3. 分享路径格式为 `/pages/activity-invite/index?code={shareCode}`。
4. 用户 B 打开分享路径后进入活动邀请页。
5. 未登录时点击「加入活动」会跳转登录页，登录成功后返回邀请页。
6. 用户 B 再次点击「加入活动」，成功后进入活动详情页。
7. 如果活动已取消或已结束，邀请页会展示不可加入原因。
8. 如果链接缺少 code 或活动不存在，会进入链接失效页。

## 成员管理验证

1. 用户 A 登录并创建活动。
2. 用户 B 通过分享邀请页加入活动。
3. 用户 A 进入活动详情，点击「成员」入口。
4. 成员列表应展示 A 和 B，A 显示创建者标签，当前用户显示“我”标签。
5. 用户 B 进入成员列表，点击「修改」设置自己的活动内昵称。
6. 用户 A 在成员列表中点击 B 的「移除」按钮。
7. 确认后成员列表刷新，B 不再显示。
8. 用户 B 再访问活动详情或成员列表应失败。
9. 用户 B 再打开分享邀请页，不能重新加入。
