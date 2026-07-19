const { cancelActivity, endActivity, getActivityDetail } = require('../../services/activity');
const { getItineraries } = require('../../services/itinerary');
const { getPolls, getSummary } = require('../../services/poll');
const { ITINERARY_STATUS, POLL_RESULT_STATUS, POLL_STATUS, formatTimeRange, label } = require('../../utils/p1-display');

const STATUS = { PLANNING: '规划中', ONGOING: '进行中', ENDED: '已结束', CANCELED: '已取消' };
const TYPE = { TRAVEL: '旅行', MEAL: '聚餐', TEAM_BUILDING: '团建', BIRTHDAY: '生日', CAMPING: '露营', DRIVE: '自驾', BOARD_GAME: '桌游', OTHER: '其他' };

Page({
  data: { loading: true, activityId: '', activity: null, summary: null, itineraries: [], polls: [], activeTab: 'ITINERARIES', errorMessage: '', actionMenuVisible: false },

  onLoad(options) {
    this.setData({ activityId: options.activityId || '' });
  },

  onShow() {
    if (this.data.activityId) this.load();
  },

  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [activity, summary, itineraries, polls] = await Promise.all([
        getActivityDetail(this.data.activityId),
        getSummary(this.data.activityId),
        getItineraries(this.data.activityId),
        getPolls(this.data.activityId)
      ]);
      this.setData({
        activity: this.normalizeActivity(activity),
        summary,
        itineraries: (itineraries || []).map((item) => ({
          ...item,
          timeText: formatTimeRange(item),
          planningStatusText: label(ITINERARY_STATUS, item.planningStatus)
        })),
        polls: (polls || []).map((item) => ({
          ...item,
          statusText: label(POLL_STATUS, item.status),
          resultApplyText: label(POLL_RESULT_STATUS, item.resultApplyStatus)
        })),
        activeTab: this.data.activeTab || summary.defaultTab
      });
    } catch (error) {
      this.setData({ errorMessage: error.message || '活动详情加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },

  normalizeActivity(activity) {
    const startDate = activity.startDate || '';
    const endDate = activity.endDate || '';
    return {
      ...activity,
      statusText: STATUS[activity.status] || activity.status,
      statusClass: (activity.status || 'PLANNING').toLowerCase(),
      typeText: TYPE[activity.type] || activity.type || '其他',
      locationText: activity.locationName || '地点待定',
      dateText: startDate && endDate && startDate !== endDate ? `${startDate} ~ ${endDate}` : startDate || endDate || '未设置日期',
      descriptionText: activity.description || '还没有活动描述',
      isCreator: activity.currentUserRole === 'CREATOR',
      isReadonly: ['ENDED', 'CANCELED'].includes(activity.status)
    };
  },

  tab(event) { this.setData({ activeTab: event.currentTarget.dataset.tab }); },
  goMembers() { wx.navigateTo({ url: `/pages/member-list/index?activityId=${this.data.activityId}` }); },
  goItineraries() { wx.navigateTo({ url: `/pages/itinerary-list/index?activityId=${this.data.activityId}` }); },
  goPolls() { wx.navigateTo({ url: `/pages/poll-list/index?activityId=${this.data.activityId}` }); },
  goItinerary(event) { wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${this.data.activityId}&itineraryId=${event.currentTarget.dataset.id}` }); },
  goPoll(event) { wx.navigateTo({ url: `/pages/poll-detail/index?activityId=${this.data.activityId}&pollId=${event.currentTarget.dataset.id}` }); },
  newItinerary() { wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}` }); },
  newPoll() { wx.navigateTo({ url: `/pages/poll-create/index?activityId=${this.data.activityId}` }); },
  todo(event) {
    const target = event.currentTarget.dataset;
    if (target.targetType === 'POLL') this.goPoll({ currentTarget: { dataset: { id: target.targetId } } });
    else this.goItinerary({ currentTarget: { dataset: { id: target.targetId } } });
  },
  copyShareCode() {
    const shareCode = this.data.activity && this.data.activity.shareCode;
    if (shareCode) wx.setClipboardData({ data: shareCode });
  },
  goEdit() {
    this.closeActionMenu();
    wx.navigateTo({ url: `/pages/activity-edit/index?activityId=${this.data.activityId}` });
  },
  toggleActionMenu() { this.setData({ actionMenuVisible: !this.data.actionMenuVisible }); },
  closeActionMenu() { this.setData({ actionMenuVisible: false }); },
  stopActionMenu() {},
  confirmEnd() {
    this.closeActionMenu();
    wx.showModal({ title: '结束活动', content: '结束后行程和投票将变为只读。', success: async (result) => {
      if (!result.confirm) return;
      try { await endActivity(this.data.activityId); this.load(); }
      catch (error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); }
    }});
  },
  confirmCancel() {
    this.closeActionMenu();
    wx.showModal({ title: '取消活动', content: '取消后内容仅可查看，不会删除历史数据。', confirmColor: '#D94C4C', success: async (result) => {
      if (!result.confirm) return;
      try { await cancelActivity(this.data.activityId); this.load(); }
      catch (error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); }
    }});
  },
  onShareAppMessage() {
    const activity = this.data.activity || {};
    return { title: `邀请你加入：${activity.name || '玩伴空间活动'}`, path: `/pages/activity-invite/index?code=${activity.shareCode || ''}` };
  }
});
