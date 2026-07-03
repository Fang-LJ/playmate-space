const { getToken, clearToken } = require('../../utils/token');

Page({
  data: {
    hasToken: false
  },

  onShow() {
    this.setData({
      hasToken: Boolean(getToken())
    });
  },

  goLogin() {
    wx.navigateTo({
      url: '/pages/login/index'
    });
  },

  clearLogin() {
    clearToken();
    this.setData({ hasToken: false });
    wx.showToast({
      title: 'token 已清理',
      icon: 'none'
    });
  }
});
