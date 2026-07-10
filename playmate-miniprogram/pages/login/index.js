const { wxLogin, getCurrentMockUser, selectMockUser, MOCK_USERS } = require('../../services/auth');
const { handleLoginSuccess } = require('../../utils/login-flow');

Page({
  data: {
    loading: false,
    redirect: '',
    mockUserLabel: ''
  },

  onLoad(options) {
    this.setData({
      redirect: options.redirect ? decodeURIComponent(options.redirect) : '',
      mockUserLabel: getCurrentMockUser().nickname
    });
  },

  async handleWxLogin() {
    if (this.data.loading) {
      return;
    }

    this.setData({ loading: true });
    try {
      const loginResult = await wxLogin();
      wx.showToast({
        title: '登录成功',
        icon: 'success'
      });
      if (loginResult.isNewUser) {
        wx.showToast({ title: '已为你创建玩伴账号', icon: 'none' });
      }
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
    this.goRedirectTarget(handleLoginSuccess(loginResult, { redirect: this.data.redirect }));
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
