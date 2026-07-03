Page({
  handleLoginTodo() {
    wx.showToast({
      title: '登录联调将在 P0-013 接入',
      icon: 'none'
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
  }
});
