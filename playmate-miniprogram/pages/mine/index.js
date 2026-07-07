const { logout, isLoggedIn } = require('../../services/auth');
const { getCurrentUser } = require('../../services/user');

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
        user: this.normalizeUser(user),
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

  goCreateActivity() {
    wx.navigateTo({
      url: '/pages/activity-create/index'
    });
  },

  goProfileEdit() {
    wx.navigateTo({
      url: '/pages/profile-edit/index'
    });
  },

  handleProfileCardTap() {
    if (this.data.isLoggedIn) {
      this.goProfileEdit();
      return;
    }
    this.goLogin();
  },

  normalizeUser(user) {
    return {
      ...user,
      nickname: user.nickname || '玩伴用户',
      phoneText: user.phone || '未填写',
      statusText: user.status === 'DISABLED' ? '已禁用' : '正常'
    };
  }
});
