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

开发者工具访问本地 HTTP 接口时，需要在本地调试环境关闭合法域名校验。

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

未实现：

- 活动列表业务
- 创建活动
- 活动详情
- 分享加入
- 成员管理
- 文件上传

## 登录联调

登录页会调用：

- `POST /api/auth/wx-login`
- `GET /api/users/me`

本地开发阶段使用 `mockOpenid`，首次登录会生成并缓存一个 mock openid。登录成功后 token 会写入本地 storage，刷新小程序后「我的」页会继续通过 token 请求当前用户信息。
