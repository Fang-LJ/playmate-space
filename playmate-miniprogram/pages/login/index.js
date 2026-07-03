const { wxLogin, getCurrentUser } = require('../../services/auth');

Page({
  data: {
    loading: false
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
      wx.switchTab({
        url: '/pages/mine/index'
      });
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
  }
});
