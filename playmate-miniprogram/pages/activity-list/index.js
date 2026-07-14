const { isLoggedIn } = require('../../services/auth');
const { getMyActivities } = require('../../services/activity');
const { getMyActivityTodos } = require('../../services/poll');
const { normalizeShareCode, buildInvitePath } = require('../../utils/share-code');

const ACTIVITY_TYPE_LABELS = {
  TRAVEL: '旅行',
  MEAL: '聚餐',
  TEAM_BUILDING: '团建',
  BIRTHDAY: '生日',
  CAMPING: '露营',
  DRIVE: '自驾',
  BOARD_GAME: '桌游',
  OTHER: '其他'
};

const STATUS_LABELS = {
  PLANNING: '规划中',
  ONGOING: '进行中',
  ENDED: '已结束',
  CANCELED: '已取消'
};

const ROLE_LABELS = {
  CREATOR: '我创建的',
  MEMBER: '我加入的'
};

Page({
  data: {
    loading: false,
    loaded: false,
    isLoggedIn: false,
    activities: [],
    displayActivities: [],
    activeFilter: 'ALL',
    filters: [
      { label: '全部', value: 'ALL' },
      { label: '规划中', value: 'PLANNING' },
      { label: '进行中', value: 'ONGOING' },
      { label: '已结束', value: 'ENDED' }
    ],
    errorMessage: '',
    todoCount: 0,
    showJoinDialog: false,
    shareCode: '',
    joinError: ''
  },

  onShow() {
    this.syncTabBar();
    this.loadActivities();
  },

  syncTabBar() {
    const tabBar = this.getTabBar && this.getTabBar();
    if (tabBar) {
      tabBar.setData({ selected: 0 });
    }
  },

  async loadActivities() {
    if (!isLoggedIn()) {
      this.setData({
        loading: false,
        loaded: true,
        isLoggedIn: false,
        activities: [],
        displayActivities: [],
        errorMessage: '',
        todoCount: 0
      });
      return;
    }

    this.setData({
      loading: true,
      isLoggedIn: true,
      errorMessage: ''
    });

    try {
      const activities = await getMyActivities();
      const normalized = (activities || []).map(this.normalizeActivity);
      const todoCount = await this.getTodoCount();
      this.setData({
        activities: normalized,
        displayActivities: this.filterActivities(normalized, this.data.activeFilter),
        loaded: true,
        todoCount
      });
    } catch (error) {
      this.setData({
        activities: [],
        displayActivities: [],
        loaded: true,
        errorMessage: error.message || '活动加载失败'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  async getTodoCount() {
    try {
      const summary = await getMyActivityTodos();
      return Number(summary.todoCount || 0);
    } catch (error) {
      return 0;
    }
  },

  normalizeActivity(activity) {
    const startDate = activity.startDate || '';
    const endDate = activity.endDate || '';
    const dateText = startDate && endDate && startDate !== endDate
      ? `${startDate} ~ ${endDate}`
      : startDate || endDate || '未设置日期';
    return {
      ...activity,
      typeText: ACTIVITY_TYPE_LABELS[activity.type] || activity.type || '其他',
      statusText: STATUS_LABELS[activity.status] || activity.status || '未知',
      statusClass: this.resolveStatusClass(activity.status),
      dateText,
      locationText: activity.locationName || '地点待定',
      roleText: ROLE_LABELS[activity.role] || activity.role || '成员'
    };
  },

  resolveStatusClass(status) {
    if (status === 'ENDED') {
      return 'ended';
    }
    if (status === 'CANCELED') {
      return 'canceled';
    }
    if (status === 'ONGOING') {
      return 'ongoing';
    }
    return 'planning';
  },

  filterActivities(activities, filter) {
    if (filter === 'ALL') {
      return activities;
    }
    return activities.filter((activity) => activity.status === filter);
  },

  changeFilter(event) {
    const { value } = event.currentTarget.dataset;
    this.setData({
      activeFilter: value,
      displayActivities: this.filterActivities(this.data.activities, value)
    });
  },

  goLogin() {
    wx.switchTab({
      url: '/pages/mine/index'
    });
  },

  goCreate() {
    if (!isLoggedIn()) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      });
      this.goLogin();
      return;
    }
    wx.navigateTo({
      url: '/pages/activity-create/index'
    });
  },

  openJoinDialog() {
    this.setData({ showJoinDialog: true, joinError: '' });
  },

  closeJoinDialog() {
    this.setData({ showJoinDialog: false, shareCode: '', joinError: '' });
  },

  stopPropagation() {},

  handleShareCodeInput(event) {
    this.setData({
      shareCode: normalizeShareCode(event.detail.value),
      joinError: ''
    });
  },

  viewInvite() {
    const target = buildInvitePath(this.data.shareCode);
    if (!target) {
      this.setData({ joinError: '请输入活动分享码' });
      return;
    }
    this.closeJoinDialog();
    wx.navigateTo({ url: target });
  },

  goTodos() {
    wx.navigateTo({ url: '/pages/activity-todos/index' });
  },

  goDetail(event) {
    const { id } = event.currentTarget.dataset;
    if (!id) {
      return;
    }
    wx.navigateTo({
      url: `/pages/activity-detail/index?activityId=${id}`
    });
  },

  onPullDownRefresh() {
    this.loadActivities()
      .finally(() => {
        wx.stopPullDownRefresh();
      });
  },

  retryLoad() {
    this.loadActivities();
  }
});
