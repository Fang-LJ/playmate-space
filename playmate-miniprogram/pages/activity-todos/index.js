const { getMyActivityTodos } = require('../../services/collaboration');
const { TODO_TYPE, formatDateTime, isVisibleTodo, visibleTodos } = require('../../utils/todo-display');
const { label } = require('../../utils/itinerary-display');

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
      visibleTodos(response && response.todos).forEach((item) => {
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
    if (!isVisibleTodo(event.currentTarget.dataset)) return;
    if (targetType === 'ITINERARY') wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${activityId}&itineraryId=${targetId}` });
    else if (targetType === 'MANUAL') wx.navigateTo({ url: `/pages/activity-detail/index?activityId=${activityId}` });
  },

  retry() { this.load(); }
});
