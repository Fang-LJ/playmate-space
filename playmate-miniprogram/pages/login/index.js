const { wxLogin, getCurrentMockUser, selectMockUser, MOCK_USERS } = require('../../services/auth');
const { getActiveEnv } = require('../../utils/config');
const { handleLoginSuccess, shouldCompleteWechatProfile } = require('../../utils/login-flow');

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
      mockUserLabel: getActiveEnv() === 'local' ? getCurrentMockUser().nickname : '',
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
    return getActiveEnv() === 'local';
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
    const target = handleLoginSuccess(loginResult, { redirect: this.data.redirect });
    if (shouldCompleteWechatProfile(loginResult)) {
      wx.redirectTo({
        url: `/pages/wechat-profile/index?redirect=${encodeURIComponent(target)}`
      });
      return;
    }
    this.goRedirectTarget(target);
  },

  goRedirectTarget(target) {
    if (target === '/pages/activity-list/index' || target === '/pages/mine/index' || target === '/pages/book-list/index') {
      wx.switchTab({ url: target });
      return;
    }
    wx.redirectTo({
      url: target
    });
  },

  chooseMockUser() {
    if (getActiveEnv() !== 'local') {
      return;
    }
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
