const { isLoggedIn } = require('../../services/auth');
const { getActivityInviteInfo, joinActivity } = require('../../services/invite');

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
    shareCode: '',
    loading: true,
    joining: false,
    invite: null,
    errorMessage: ''
  },

  onLoad(options) {
    const shareCode = options.code || '';
    if (!shareCode) {
      wx.redirectTo({
        url: '/pages/link-invalid/index'
      });
      return;
    }
    this.setData({ shareCode });
  },

  onShow() {
    if (this.data.shareCode) {
      this.loadInvite();
    }
  },

  async loadInvite() {
    this.setData({
      loading: true,
      errorMessage: ''
    });
    try {
      const invite = await getActivityInviteInfo(this.data.shareCode);
      this.setData({
        invite: this.normalizeInvite(invite)
      });
    } catch (error) {
      if (error.code === 'NOT_FOUND' || error.statusCode === 404) {
        wx.redirectTo({
          url: '/pages/link-invalid/index'
        });
        return;
      }
      this.setData({
        errorMessage: error.message || '邀请信息加载失败'
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  normalizeInvite(invite) {
    const startDate = invite.startDate || '';
    const endDate = invite.endDate || '';
    return {
      ...invite,
      typeText: ACTIVITY_TYPE_LABELS[invite.type] || invite.type || '其他',
      statusText: STATUS_LABELS[invite.status] || invite.status || '未知',
      dateText: startDate && endDate && startDate !== endDate
        ? `${startDate} ~ ${endDate}`
        : startDate || endDate || '未设置日期',
      locationText: invite.locationName || '地点待定',
      descriptionText: invite.description || '邀请你加入这个活动空间，一起规划和协作。'
    };
  },

  async handleJoin() {
    const invite = this.data.invite;
    if (!invite) {
      return;
    }
    if (invite.joined) {
      this.goDetail();
      return;
    }
    if (!invite.canJoin) {
      wx.showToast({
        title: invite.reason || '当前不可加入',
        icon: 'none'
      });
      return;
    }
    if (!isLoggedIn()) {
      const redirect = encodeURIComponent(`/pages/activity-invite/index?code=${this.data.shareCode}`);
      wx.navigateTo({
        url: `/pages/login/index?redirect=${redirect}`
      });
      return;
    }
    if (this.data.joining) {
      return;
    }
    this.setData({ joining: true });
    try {
      const result = await joinActivity(this.data.shareCode);
      wx.showToast({
        title: result.message || '加入成功',
        icon: 'success'
      });
      setTimeout(() => {
        wx.redirectTo({
          url: `/pages/activity-detail/index?activityId=${result.activityId}`
        });
      }, 500);
    } catch (error) {
      wx.showToast({
        title: error.message || '加入失败',
        icon: 'none'
      });
      this.loadInvite();
    } finally {
      this.setData({ joining: false });
    }
  },

  goDetail() {
    const invite = this.data.invite;
    if (!invite || !invite.activityId) {
      return;
    }
    wx.redirectTo({
      url: `/pages/activity-detail/index?activityId=${invite.activityId}`
    });
  },

  goHome() {
    wx.switchTab({
      url: '/pages/activity-list/index'
    });
  },

  retryLoad() {
    this.loadInvite();
  }
});
