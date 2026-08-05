const { cancelActivity, endActivity, getActivityDetail } = require('../../services/activity');
const { getItineraries, deleteItinerary: removeItinerary } = require('../../services/itinerary');
const { getPolls, getSummary } = require('../../services/poll');
const { getActivityMembers } = require('../../services/member');
const { getCurrentUser } = require('../../services/user');
const { getExpenseSummary, completeSettlement } = require('../../services/expense');
const { POLL_RESULT_STATUS, POLL_STATUS, label } = require('../../utils/p1-display');
const { buildCardViewModel } = require('../../utils/itinerary-ui');

const STATUS = { PLANNING: '规划中', ONGOING: '进行中', ENDED: '已结束', CANCELED: '已取消' };
const TYPE = { TRAVEL: '旅行', MEAL: '聚餐', TEAM_BUILDING: '团建', BIRTHDAY: '生日', CAMPING: '露营', DRIVE: '自驾', BOARD_GAME: '桌游', OTHER: '其他' };
const EXPENSE_CATEGORY = { TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', FOOD: '餐饮', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };

Page({
  data: { loading: true, activityId: '', activity: null, summary: null, itineraries: [], polls: [], members: [], activeTab: 'ITINERARIES', expenseSummary: null, expenseLoading: false, errorMessage: '', actionMenuVisible: false, openItineraryId: null },

  onLoad(options) {
    this.setData({ activityId: options.activityId || '' });
  },

  onShow() {
    if (this.data.activityId) this.load();
  },

  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [activity, summary, itineraries, polls, members, currentUser] = await Promise.all([
        getActivityDetail(this.data.activityId),
        getSummary(this.data.activityId),
        getItineraries(this.data.activityId),
        getPolls(this.data.activityId),
        getActivityMembers(this.data.activityId).catch(() => []),
        getCurrentUser()
      ]);
      const normalizedActivity = this.normalizeActivity(activity);
      const currentUserId = String(currentUser.userId);
      this.setData({
        activity: normalizedActivity,
        summary,
        itineraries: (itineraries || []).map((item) => ({
          ...buildCardViewModel(item),
          canEdit: !normalizedActivity.isReadonly && item.planningStatus !== 'CANCELED'
            && (normalizedActivity.isCreator || String(item.createdBy) === currentUserId),
          canDelete: !normalizedActivity.isReadonly
            && (normalizedActivity.isCreator || String(item.createdBy) === currentUserId)
        })),
        polls: (polls || []).map((item) => ({
          ...item,
          statusText: label(POLL_STATUS, item.status),
          resultApplyText: label(POLL_RESULT_STATUS, item.resultApplyStatus)
        })),
        members: (members || []).slice(0, 4).map((member) => ({
          ...member,
          avatarText: (member.nickname || '玩').slice(0, 1)
        })),
        activeTab: this.data.activeTab || summary.defaultTab,
        openItineraryId: null
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

  tab(event) { const activeTab = event.currentTarget.dataset.tab; this.setData({ activeTab }); if (activeTab === 'COSTS') this.loadExpenseSummary(); },
  async loadExpenseSummary() {
    this.setData({ expenseLoading: true });
    try { const summary = await getExpenseSummary(this.data.activityId); this.setData({ expenseSummary: { ...summary, recentExpenses: (summary.recentExpenses || []).map(item => ({ ...item, categoryText: EXPENSE_CATEGORY[item.category] || item.category })), mySuggestions: (summary.mySuggestions || []).map(item => ({ ...item, isPayer: item.currentUserCanComplete })) } }); }
    catch (error) { wx.showToast({ title: error.message || '费用摘要加载失败', icon: 'none' }); }
    finally { this.setData({ expenseLoading: false }); }
  },
  goMembers() { wx.navigateTo({ url: `/pages/member-list/index?activityId=${this.data.activityId}` }); },
  goItineraries() { wx.navigateTo({ url: `/pages/itinerary-list/index?activityId=${this.data.activityId}` }); },
  goPolls() { wx.navigateTo({ url: `/pages/poll-list/index?activityId=${this.data.activityId}` }); },
  goItinerary(event) {
    const itineraryId = event.detail ? event.detail.itineraryId : event.currentTarget.dataset.id;
    wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${this.data.activityId}&itineraryId=${itineraryId}` });
  },
  openItineraryActions(event) { this.setData({ openItineraryId: event.detail.itineraryId }); },
  closeItineraryActions() { this.setData({ openItineraryId: null }); },
  editItinerary(event) {
    this.closeItineraryActions();
    wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}&itineraryId=${event.detail.itineraryId}` });
  },
  deleteItinerary(event) {
    const itineraryId = event.detail.itineraryId;
    this.closeItineraryActions();
    wx.showModal({ title: '删除行程', content: '删除后无法恢复，是否继续？', confirmColor: '#D94C4C', success: async (result) => {
      if (!result.confirm) return;
      try {
        await removeItinerary(this.data.activityId, itineraryId);
        wx.showToast({ title: '已删除', icon: 'success' });
        this.load();
      } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }); }
    }});
  },
  goPoll(event) { wx.navigateTo({ url: `/pages/poll-detail/index?activityId=${this.data.activityId}&pollId=${event.currentTarget.dataset.id}` }); },
  newItinerary() { wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}` }); },
  newPoll() { wx.navigateTo({ url: `/pages/poll-create/index?activityId=${this.data.activityId}` }); },
  goExpenses() { wx.navigateTo({ url: `/pages/expense-detail/index?activityId=${this.data.activityId}` }); },
  newExpense() { wx.navigateTo({ url: `/pages/expense-edit/index?activityId=${this.data.activityId}` }); },
  goExpenseItem(event) { wx.navigateTo({ url: `/pages/expense-item-detail/index?activityId=${this.data.activityId}&expenseId=${event.currentTarget.dataset.id}` }); },
  async completeExpenseSettlement(event) { const item = event.currentTarget.dataset.item; try { await completeSettlement(this.data.activityId, { fromUserId: item.fromUserId, toUserId: item.toUserId, amount: item.amount }); wx.showToast({ title: '已记录转账', icon: 'success' }); this.loadExpenseSummary(); } catch (error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); } },
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
