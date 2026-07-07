const { cancelActivity, endActivity, getActivityDetail } = require('../../services/activity');

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
  CREATOR: '创建者',
  MEMBER: '成员'
};

Page({
  data: {
    loading: true,
    activityId: '',
    activity: null,
    errorMessage: '',
    beforeModules: [
      { name: '成员', shortName: '成', desc: '查看成员', route: 'members', tone: 'green', enabled: true },
      { name: '行程', shortName: '行', desc: '暂未开放', tone: 'gray', enabled: false },
      { name: '投票', shortName: '投', desc: '暂未开放', tone: 'yellow', enabled: false }
    ],
    afterModules: [
      { name: '账本', shortName: '账', desc: '暂未开放', tone: 'warm', enabled: false },
      { name: 'AA 结算', shortName: 'A', desc: '暂未开放', tone: 'gray', enabled: false },
      { name: '照片墙', shortName: '照', desc: '暂未开放', tone: 'gray', enabled: false }
    ]
  },

  onLoad(options) {
    const activityId = options.activityId;
    this.setData({ activityId });
  },

  onShow() {
    if (this.data.activityId) {
      this.loadDetail(this.data.activityId);
    }
  },

  async loadDetail(activityId) {
    if (!activityId) {
      this.setData({
        loading: false,
        errorMessage: '缺少活动 ID'
      });
      return;
    }

    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const activity = await getActivityDetail(activityId);
      this.setData({
        activity: this.normalizeActivity(activity)
      });
    } catch (error) {
      this.setData({
        errorMessage: error.message || '活动详情加载失败'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  normalizeActivity(activity) {
    const startDate = activity.startDate || '';
    const endDate = activity.endDate || '';
    return {
      ...activity,
      typeText: ACTIVITY_TYPE_LABELS[activity.type] || activity.type || '其他',
      statusText: STATUS_LABELS[activity.status] || activity.status || '未知',
      statusClass: this.resolveStatusClass(activity.status),
      dateText: startDate && endDate && startDate !== endDate
        ? `${startDate} ~ ${endDate}`
        : startDate || endDate || '未设置日期',
      locationText: activity.locationName || '地点待定',
      addressText: activity.address || '暂无详细地址',
      descriptionText: activity.description || '还没有活动描述',
      currentUserRoleText: ROLE_LABELS[activity.currentUserRole] || activity.currentUserRole || '成员',
      isCreator: activity.currentUserRole === 'CREATOR',
      isPlanning: activity.status === 'PLANNING',
      isEnded: activity.status === 'ENDED',
      isCanceled: activity.status === 'CANCELED'
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

  retryLoad() {
    this.loadDetail(this.data.activityId);
  },

  showTodo(event) {
    const { name, route } = event.currentTarget.dataset;
    if (route === 'members') {
      wx.navigateTo({
        url: `/pages/member-list/index?activityId=${this.data.activityId}`
      });
      return;
    }
    wx.showToast({
      title: `${name}暂未开放`,
      icon: 'none'
    });
  },

  onShareAppMessage() {
    const activity = this.data.activity || {};
    return {
      title: `邀请你加入：${activity.name || '玩伴空间活动'}`,
      path: `/pages/activity-invite/index?code=${activity.shareCode || ''}`
    };
  },

  copyShareCode() {
    const shareCode = this.data.activity && this.data.activity.shareCode;
    if (!shareCode) {
      return;
    }
    wx.setClipboardData({
      data: shareCode
    });
  },

  goEdit() {
    wx.navigateTo({
      url: `/pages/activity-edit/index?activityId=${this.data.activityId}`
    });
  },

  confirmEnd() {
    wx.showModal({
      title: '结束活动',
      content: '结束后仍可编辑封面和描述，但不能再修改名称、时间和地点。',
      confirmText: '结束活动',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          const activity = await endActivity(this.data.activityId);
          this.setData({ activity: this.normalizeActivity(activity) });
          wx.showToast({ title: '已结束', icon: 'success' });
        } catch (error) {
          wx.showToast({ title: error.message || '操作失败', icon: 'none' });
        }
      }
    });
  },

  confirmCancel() {
    wx.showModal({
      title: '取消活动',
      content: '取消后活动将变为只读，不会删除历史数据。',
      confirmText: '取消活动',
      confirmColor: '#D94C4C',
      success: async (res) => {
        if (!res.confirm) {
          return;
        }
        try {
          const activity = await cancelActivity(this.data.activityId);
          this.setData({ activity: this.normalizeActivity(activity) });
          wx.showToast({ title: '已取消', icon: 'success' });
        } catch (error) {
          wx.showToast({ title: error.message || '操作失败', icon: 'none' });
        }
      }
    });
  }
});
