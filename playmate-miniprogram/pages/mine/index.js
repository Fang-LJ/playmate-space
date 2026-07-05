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
      title: '已退出登录',
      icon: 'none'
    });
  },

  goActivities() {
    wx.switchTab({
      url: '/pages/activity-list/index'
    });
  },

  showAbout() {
    wx.showModal({
      title: '关于玩伴空间',
      content: '玩伴空间用于朋友聚会、旅行和团建活动协作。第一版已支持创建活动、邀请加入和成员管理。',
      showCancel: false,
      confirmText: '知道了'
    });
  }
});
