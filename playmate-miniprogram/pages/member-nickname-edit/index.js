const { updateMyActivityNickname } = require('../../services/member');

Page({
  data: {
    activityId: '',
    nickname: '',
    submitting: false
  },

  onLoad(options) {
    this.setData({
      activityId: options.activityId || '',
      nickname: options.nickname ? decodeURIComponent(options.nickname) : ''
    });
  },

  handleInput(event) {
    this.setData({
      nickname: event.detail.value
    });
  },

  async submitNickname() {
    if (this.data.submitting) {
      return;
    }
    const nickname = this.data.nickname.trim();
    if (!nickname) {
      wx.showToast({
        title: '请填写昵称',
        icon: 'none'
      });
      return;
    }
    if (nickname.length > 64) {
      wx.showToast({
        title: '昵称不能超过 64 个字符',
        icon: 'none'
      });
      return;
    }

    this.setData({ submitting: true });
    try {
      await updateMyActivityNickname(this.data.activityId, nickname);
      wx.showToast({
        title: '已保存',
        icon: 'success'
      });
      setTimeout(() => {
        wx.navigateBack();
      }, 500);
    } catch (error) {
      wx.showToast({
        title: error.message || '保存失败',
        icon: 'none'
      });
    } finally {
      this.setData({ submitting: false });
    }
  },

  cancelBack() {
    wx.navigateBack();
  }
});
