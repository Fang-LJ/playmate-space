const { getPollDetail, submitVote, closePoll, applyPollResult, previewPollResult } = require('../../services/poll');
const { getActivityDetail } = require('../../services/activity');
const { getCurrentUser } = require('../../services/user');
const { POLL_DECISION, POLL_PURPOSE, POLL_RESULT_STATUS, POLL_STATUS, formatDateTime, hasVisibleText, label } = require('../../utils/p1-display');

Page({
  data: {
    activityId: '', pollId: '', poll: null, loading: true, errorMessage: '',
    selected: [], submitting: false, canManage: false, canApply: false, preview: null, previewing: false
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
      const [poll, activity, currentUser] = await Promise.all([
        getPollDetail(this.data.activityId, this.data.pollId),
        getActivityDetail(this.data.activityId),
        getCurrentUser()
      ]);
      const decorated = this.decoratePoll(poll);
      const currentUserId = String(currentUser.userId);
      const isActivityCreator = activity.currentUserRole === 'CREATOR';
      const isPollCreator = currentUserId === String(poll.createdBy);
      const isTargetItineraryCreator = poll.targetItinerary
        && currentUserId === String(poll.targetItinerary.createdBy);
      this.setData({
        poll: decorated,
        selected: decorated.currentUserOptionIds,
        canManage: isActivityCreator || isPollCreator,
        canApply: isActivityCreator || isPollCreator || isTargetItineraryCreator,
        preview: this.decoratePreview(poll.resultPreview)
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
      scopeText: (poll.decisionScopeLabels || []).join('、') || '不修改行程',
      unchangedText: (poll.unchangedFieldLabels || []).join('、') || '无',
      applicationHistory: (poll.applicationHistory || []).map((history) => ({
        ...history,
        appliedAtText: formatDateTime(history.appliedAt),
        operatorText: history.appliedByName || `用户 ${history.appliedBy}`,
        changedFields: (history.changedFields || []).map((change) => ({
          ...change, beforeText: this.displayValue(change.beforeValue), afterText: this.displayValue(change.afterValue)
        })),
        unchangedText: (history.unchangedFields || []).map((field) => field.label).join('、')
      })),
      options: (poll.options || []).map((item) => ({
        ...item,
        optionId: Number(item.optionId),
        selected: singleSelected.includes(Number(item.optionId)),
        hasDescription: hasVisibleText(item.optionDescription),
        voteText: `${item.voteCount || 0} 票`
      }))
    };
  },

  decoratePreview(preview) {
    if (!preview) return null;
    return {
      ...preview,
      changedFields: (preview.changedFields || []).map((change) => ({
        ...change,
        beforeText: this.displayValue(change.beforeValue),
        afterText: this.displayValue(change.afterValue)
      })),
      unchangedText: (preview.unchangedFields || []).map((field) => field.label).join('、')
    };
  },

  displayValue(value) {
    return value === null || value === undefined || String(value).trim() === '' ? '未设置' : String(value);
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
    const optionId = Number(event.currentTarget.dataset.id || (this.data.preview && this.data.preview.optionId));
    try {
      const poll = this.decoratePoll(await applyPollResult(this.data.activityId, this.data.pollId, optionId));
      this.setData({ poll, selected: poll.currentUserOptionIds, preview: null });
      wx.showToast({ title: '结果已应用', icon: 'success' });
    } catch (error) {
      wx.showToast({ title: error.message || '应用失败', icon: 'none' });
    }
  },

  async previewResult(event) {
    const optionId = Number(event.currentTarget.dataset.id);
    this.setData({ previewing: true });
    try {
      this.setData({ preview: this.decoratePreview(
        await previewPollResult(this.data.activityId, this.data.pollId, optionId)
      ) });
    } catch (error) {
      wx.showToast({ title: error.message || '预览失败', icon: 'none' });
    } finally {
      this.setData({ previewing: false });
    }
  },

  keepOriginal() {
    this.setData({ preview: null });
  },

  goItinerary() {
    const id = this.data.poll.generatedItineraryId || this.data.poll.targetItineraryId;
    if (id) wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${this.data.activityId}&itineraryId=${id}` });
  }
});
