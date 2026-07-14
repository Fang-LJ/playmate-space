const { getMyActivityTodos } = require('../../services/poll');
const { TODO_TYPE, formatDateTime, label } = require('../../utils/p1-display');

Page({
  data: { loading: true, errorMessage: '', groups: [] },

  onShow() {
    this.load();
  },

  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const response = await getMyActivityTodos();
      const grouped = {};
      (response.todos || []).forEach((item) => {
        const key = String(item.activityId);
        if (!grouped[key]) grouped[key] = { activityId: item.activityId, activityName: item.activityName, todos: [] };
        grouped[key].todos.push({
          ...item,
          typeText: label(TODO_TYPE, item.todoType, '待处理'),
          dueText: formatDateTime(item.dueAt),
          descriptionText: item.description || ''
        });
      });
      this.setData({ groups: Object.values(grouped) });
    } catch (error) {
      this.setData({ errorMessage: error.message || '待办加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },

  openTodo(event) {
    const { activityId, targetId, targetType } = event.currentTarget.dataset;
    if (targetType === 'POLL') {
      wx.navigateTo({ url: `/pages/poll-detail/index?activityId=${activityId}&pollId=${targetId}` });
      return;
    }
    wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${activityId}&itineraryId=${targetId}` });
  },

  retry() { this.load(); }
});
