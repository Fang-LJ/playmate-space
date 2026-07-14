const { getPollDetail, submitVote, closePoll, applyPollResult } = require('../../services/poll');
const { getActivityDetail } = require('../../services/activity');
const { POLL_DECISION, POLL_PURPOSE, POLL_RESULT_STATUS, POLL_STATUS, formatDateTime, hasVisibleText, label } = require('../../utils/p1-display');

Page({
  data: {
    activityId: '', pollId: '', poll: null, loading: true, errorMessage: '',
    selected: [], submitting: false, canManage: false, canApply: false
  },

  onLoad(options) {
    this.setData({ activityId: options.activityId || '', pollId: options.pollId || '' });
  },

  onShow() {
    if (this.data.pollId) this.load();
  },

  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [poll, activity] = await Promise.all([
        getPollDetail(this.data.activityId, this.data.pollId),
        getActivityDetail(this.data.activityId)
      ]);
      const decorated = this.decoratePoll(poll);
      const isCreator = activity.currentUserRole === 'CREATOR';
      this.setData({
        poll: decorated,
        selected: decorated.currentUserOptionIds,
        canManage: isCreator || String(poll.createdBy) === String(activity.creatorUserId),
        canApply: isCreator || String(poll.createdBy) === String(activity.creatorUserId)
      });
    } catch (error) {
      this.setData({ errorMessage: error.message || '加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },

  decoratePoll(poll) {
    const all = Array.isArray(poll.currentUserOptionIds) ? poll.currentUserOptionIds : [];
    const selected = [...new Set(all.map((value) => Number(value)).filter((value) => Number.isFinite(value)))];
    const singleSelected = poll.voteType === 'SINGLE' && selected.length > 1 ? [selected[selected.length - 1]] : selected;
    return {
      ...poll,
      currentUserOptionIds: singleSelected,
      decisionText: label(POLL_DECISION, poll.decisionType),
      purposeText: label(POLL_PURPOSE, poll.purpose),
      statusText: label(POLL_STATUS, poll.status),
      resultApplyText: label(POLL_RESULT_STATUS, poll.resultApplyStatus),
      deadlineText: formatDateTime(poll.deadline) || '未设置截止时间',
      options: (poll.options || []).map((item) => ({
        ...item,
        optionId: Number(item.optionId),
        selected: singleSelected.includes(Number(item.optionId)),
        hasDescription: hasVisibleText(item.optionDescription),
        voteText: `${item.voteCount || 0} 票`
      }))
    };
  },

  select(event) {
    if (!this.data.poll || this.data.poll.status !== 'ACTIVE') return;
    const id = Number(event.currentTarget.dataset.id);
    let selected = this.data.selected || [];
    if (this.data.poll.voteType === 'SINGLE') {
      selected = [id];
    } else {
      selected = selected.includes(id) ? selected.filter((value) => value !== id) : selected.concat(id);
    }
    this.setData({
      selected,
      'poll.options': this.data.poll.options.map((item) => ({ ...item, selected: selected.includes(Number(item.optionId)) }))
    });
  },

  async vote() {
    if (!this.data.selected.length) return wx.showToast({ title: '请选择选项', icon: 'none' });
    this.setData({ submitting: true });
    try {
      const poll = this.decoratePoll(await submitVote(this.data.activityId, this.data.pollId, this.data.selected));
      this.setData({ poll, selected: poll.currentUserOptionIds });
      wx.showToast({ title: '已提交', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '提交失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  close() {
    wx.showModal({
      title: '结束投票', content: '结束后成员不能再修改投票，是否继续？',
      success: async (result) => {
        if (!result.confirm) return;
        try {
          const poll = this.decoratePoll(await closePoll(this.data.activityId, this.data.pollId));
          this.setData({ poll, selected: poll.currentUserOptionIds });
          wx.showToast({ title: '投票已结束', icon: 'success' });
        } catch (error) {
          wx.showToast({ title: error.message || '结束失败', icon: 'none' });
        }
      }
    });
  },

  async apply(event) {
    try {
      const poll = this.decoratePoll(await applyPollResult(this.data.activityId, this.data.pollId, Number(event.currentTarget.dataset.id)));
      this.setData({ poll, selected: poll.currentUserOptionIds });
      wx.showToast({ title: '结果已应用', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '应用失败', icon: 'none' });
    }
  },

  goItinerary() {
    const id = this.data.poll.generatedItineraryId || this.data.poll.targetItineraryId;
    if (id) wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${this.data.activityId}&itineraryId=${id}` });
  }
});
