const { wxLogin, getCurrentUser } = require('../../services/auth');

Page({
  data: {
    loading: false,
    redirect: ''
  },

  onLoad(options) {
    this.setData({
      redirect: options.redirect ? decodeURIComponent(options.redirect) : ''
    });
  },

  async handleLogin() {
    if (this.data.loading) {
      return;
    }

    this.setData({ loading: true });
    try {
      await wxLogin();
      await getCurrentUser();
      wx.showToast({
        title: '登录成功',
        icon: 'success'
      });
      this.goAfterLogin();
    } catch (error) {
      wx.showToast({
        title: error.message || '登录失败',
        icon: 'none'
      });
    } finally {
      this.setData({ loading: false });
    }
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

  goAfterLogin() {
    const redirect = this.data.redirect;
    if (!redirect) {
      wx.switchTab({
        url: '/pages/mine/index'
      });
      return;
    }
    if (redirect === '/pages/activity-list/index' || redirect === '/pages/mine/index') {
      wx.switchTab({ url: redirect });
      return;
    }
    wx.redirectTo({
      url: redirect
    });
  }
});
