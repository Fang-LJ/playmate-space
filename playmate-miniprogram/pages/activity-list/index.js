const { isLoggedIn } = require('../../services/auth');
const { getMyActivities } = require('../../services/activity');

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

Page({
  data: {
    loading: false,
    loaded: false,
    isLoggedIn: false,
    activities: [],
    errorMessage: ''
  },

  onShow() {
    this.loadActivities();
  },

  async loadActivities() {
    if (!isLoggedIn()) {
      this.setData({
        loading: false,
        loaded: true,
        isLoggedIn: false,
        activities: [],
        errorMessage: ''
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
      this.setData({
        activities: (activities || []).map(this.normalizeActivity),
        loaded: true
      });
    } catch (error) {
      this.setData({
        activities: [],
        loaded: true,
        errorMessage: error.message || '活动加载失败'
      });
    } finally {
      this.setData({ loading: false });
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
      dateText,
      locationText: activity.locationName || '地点待定'
    };
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
