const { normalizeShareCode, buildInvitePath } = require('../../utils/share-code');

Page({
  data: {
    shareCode: ''
  },

  handleInput(event) {
    this.setData({ shareCode: normalizeShareCode(event.detail.value) });
  },

  viewActivity() {
    const target = buildInvitePath(this.data.shareCode);
    if (!target) {
      wx.showToast({ title: '请输入活动分享码', icon: 'none' });
      return;
    }
    wx.navigateTo({ url: target });
  },

  goActivities() {
    wx.switchTab({ url: '/pages/activity-list/index' });
  }
});
