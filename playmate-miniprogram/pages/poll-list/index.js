const { getPolls } = require('../../services/poll');
const { getActivityDetail } = require('../../services/activity');
const { POLL_DECISION, POLL_RESULT_STATUS, POLL_STATUS, formatDateTime, label } = require('../../utils/p1-display');

Page({
  data: { activityId: '', loading: true, errorMessage: '', polls: [], visiblePolls: [], filter: 'ALL', readOnly: false, filters: ['ALL', 'ACTIVE', 'CLOSED'] },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [polls, activity] = await Promise.all([getPolls(this.data.activityId), getActivityDetail(this.data.activityId)]);
      this.setData({ polls: (polls || []).map((item) => ({ ...item, decisionText: label(POLL_DECISION, item.decisionType), statusText: label(POLL_STATUS, item.status), resultText: label(POLL_RESULT_STATUS, item.resultApplyStatus), deadlineText: formatDateTime(item.deadline) })), readOnly: ['ENDED', 'CANCELED'].includes(activity.status) });
      this.applyFilter();
    } catch (error) { this.setData({ errorMessage: error.message || '加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  choose(event) { this.setData({ filter: event.currentTarget.dataset.value }); this.applyFilter(); },
  applyFilter() { const filter = this.data.filter; this.setData({ visiblePolls: filter === 'ALL' ? this.data.polls : this.data.polls.filter((item) => item.status === filter) }); },
  goCreate() { wx.navigateTo({ url: `/pages/poll-create/index?activityId=${this.data.activityId}` }); },
  goDetail(event) { wx.navigateTo({ url: `/pages/poll-detail/index?activityId=${this.data.activityId}&pollId=${event.currentTarget.dataset.id}` }); }
});
