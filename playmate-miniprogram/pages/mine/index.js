const { getCurrentUser, logout, isLoggedIn } = require('../../services/auth');
const { chooseImage, uploadActivityCover } = require('../../services/file');

Page({
  data: {
    loading: false,
    uploadLoading: false,
    user: null,
    isLoggedIn: false,
    uploadResult: null
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

  async chooseAndUploadCover() {
    if (!this.data.isLoggedIn) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      return;
    }
    if (this.data.uploadLoading) {
      return;
    }

    this.setData({ uploadLoading: true });
    try {
      const filePath = await chooseImage();
      const uploadResult = await uploadActivityCover(filePath);
      this.setData({ uploadResult });
      wx.showToast({
        title: '上传成功',
        icon: 'success'
      });
    } catch (error) {
      if (error.code === 'UNAUTHORIZED') {
        this.setLoggedOut();
      }
      wx.showToast({
        title: error.message || '上传失败',
        icon: 'none'
      });
    } finally {
      this.setData({ uploadLoading: false });
    }
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
