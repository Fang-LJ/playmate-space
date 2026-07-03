const { getCurrentUser, logout, isLoggedIn } = require('../../services/auth');

Page({
  data: {
    loading: false,
    user: null,
    isLoggedIn: false
  },

  onShow() {
    this.loadCurrentUser();
  },

  async loadCurrentUser() {
    if (!isLoggedIn()) {
      this.setLoggedOut();
      return;
    }

    this.setData({ loading: true });
    try {
      const user = await getCurrentUser();
      this.setData({
        user,
        isLoggedIn: true
      });
    } catch (error) {
      this.setLoggedOut();
    } finally {
      this.setData({ loading: false });
    }
  },

  setLoggedOut() {
    this.setData({
      user: null,
      isLoggedIn: false
    });
  },

  goLogin() {
    wx.navigateTo({
      url: '/pages/login/index'
    });
  },

  clearLogin() {
    logout();
    this.setLoggedOut();
    wx.showToast({
      title: 'token 已清理',
      icon: 'none'
    });
  },

  verifyUnauthorized() {
    logout();
    this.setData({ loading: true });
    getCurrentUser()
      .catch(() => {
        this.setLoggedOut();
      })
      .finally(() => {
        this.setData({ loading: false });
      });
  }
});
