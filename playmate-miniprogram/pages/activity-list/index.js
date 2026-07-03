const { request } = require('../../utils/request');

Page({
  data: {
    healthMessage: ''
  },

  handleCreateTodo() {
    wx.showToast({
      title: '创建活动将在后续任务接入',
      icon: 'none'
    });
  },

  async handleHealthCheck() {
    try {
      const data = await request({
        url: '/api/health',
        requireAuth: false
      });
      this.setData({
        healthMessage: `后端连接正常：${JSON.stringify(data)}`
      });
    } catch (error) {
      this.setData({
        healthMessage: error.message || '后端连接失败'
      });
    }
  }
});
