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

未实现：

- 分享加入
- 成员管理
- 行程、投票、账本、AA、照片墙

## 登录联调

登录页会调用：

- `POST /api/auth/wx-login`
- `GET /api/users/me`

本地开发阶段使用 `mockOpenid`，首次登录会生成并缓存一个 mock openid。登录成功后 token 会写入本地 storage，刷新小程序后「我的」页会继续通过 token 请求当前用户信息。

## 上传测试

「我的」页包含一个临时的活动封面上传测试区域：

1. 先完成登录。
2. 点击「选择并上传图片」。
3. 小程序会通过 `wx.uploadFile` 调用 `POST /api/files/upload`。
4. 请求 header 会携带 `Authorization: Bearer token`。
5. `formData.fileType` 固定为 `ACTIVITY_COVER`。
6. 上传成功后页面展示 `fileId`、`url`、`contentType`、`size`，并尝试展示图片预览。

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
