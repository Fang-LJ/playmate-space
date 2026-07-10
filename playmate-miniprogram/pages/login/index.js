const { wxLogin, getCurrentMockUser, selectMockUser, MOCK_USERS } = require('../../services/auth');
const { handleLoginSuccess, shouldPromptWechatProfile, markWechatProfilePrompted } = require('../../utils/login-flow');

Page({
  data: {
    loading: false,
    redirect: '',
    mockUserLabel: '',
    showDevNote: false,
    safeTop: 160
  },

  onLoad(options) {
    this.setData({
      redirect: options.redirect ? decodeURIComponent(options.redirect) : '',
      mockUserLabel: getCurrentMockUser().nickname,
      showDevNote: this.isDevelopmentEnvironment(),
      safeTop: this.getSafeTop()
    });
  },

  getSafeTop() {
    try {
      const windowInfo = wx.getWindowInfo();
      const menuButton = wx.getMenuButtonBoundingClientRect();
      const menuBottom = menuButton && menuButton.bottom ? menuButton.bottom : 0;
      const statusBarHeight = windowInfo.statusBarHeight || 0;
      const topInPx = Math.max(80, menuBottom + 20, statusBarHeight + 56);
      return Math.round(topInPx * 750 / windowInfo.windowWidth);
    } catch (error) {
      return 160;
    }
  },

  isDevelopmentEnvironment() {
    try {
      return wx.getAccountInfoSync().miniProgram.envVersion === 'develop';
    } catch (error) {
      return false;
    }
  },

  async handleWxLogin() {
    if (this.data.loading) {
      return;
    }

    this.setData({ loading: true });
    try {
      const loginResult = await wxLogin();
      this.goAfterLogin(loginResult);
    } catch (error) {
      wx.showToast({
        title: error.message || '登录失败',
        icon: 'none'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  goAccountLogin() {
    const redirect = encodeURIComponent(this.data.redirect || '');
    wx.navigateTo({
      url: `/pages/account-login/index?redirect=${redirect}`
    });
  },

  goRegister() {
    const redirect = encodeURIComponent(this.data.redirect || '');
    wx.navigateTo({
      url: `/pages/account-register/index?redirect=${redirect}`
    });
  },

  goBack() {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      wx.navigateBack();
      return;
    }
    wx.switchTab({
      url: '/pages/activity-list/index'
    });
  },

  goAfterLogin(loginResult) {
    const shouldPrompt = shouldPromptWechatProfile(loginResult);
    const target = handleLoginSuccess(loginResult, { redirect: this.data.redirect });
    if (!shouldPrompt) {
      this.goRedirectTarget(target);
      return;
    }
    wx.showModal({
      title: '完善微信资料',
      content: '可以使用微信头像、昵称和手机号，方便朋友在活动中识别你。也可以稍后设置。',
      confirmText: '现在设置',
      cancelText: '以后再说',
      success: (result) => {
        markWechatProfilePrompted(loginResult.userId);
        if (result.confirm) {
          wx.redirectTo({
            url: `/pages/wechat-profile/index?redirect=${encodeURIComponent(target)}`
          });
          return;
        }
        this.goRedirectTarget(target);
      },
      fail: () => this.goRedirectTarget(target)
    });
  },

  goRedirectTarget(target) {
    if (target === '/pages/activity-list/index' || target === '/pages/mine/index') {
      wx.switchTab({ url: target });
      return;
    }
    wx.redirectTo({
      url: target
    });
  },

  chooseMockUser() {
    wx.showActionSheet({
      itemList: MOCK_USERS.map((user) => `模拟用户 ${user.key} · ${user.nickname}`),
      success: ({ tapIndex }) => {
        const user = selectMockUser(MOCK_USERS[tapIndex].key);
        this.setData({ mockUserLabel: user.nickname });
        wx.showToast({ title: `已切换为${user.nickname}`, icon: 'none' });
      }
    });
  }
});
