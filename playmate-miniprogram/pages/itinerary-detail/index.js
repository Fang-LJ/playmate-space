const { getItineraryDetail, cancelItinerary, deleteItinerary, restoreItinerary } = require('../../services/itinerary');
const { ITINERARY_STATUS, ITINERARY_TYPE, POLL_RESULT_STATUS, POLL_STATUS, formatTimeRange, itinerarySummary, label } = require('../../utils/p1-display');

Page({
  data: { activityId: '', itineraryId: '', detail: null, loading: true, errorMessage: '', actionMenuVisible: false },
  onLoad(options) { this.setData({ activityId: options.activityId || '', itineraryId: options.itineraryId || '' }); },
  onShow() { if (this.data.itineraryId) this.load(); },
  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const detail = await getItineraryDetail(this.data.activityId, this.data.itineraryId);
      this.setData({
        detail: {
          ...detail,
          itinerary: {
            ...detail.itinerary,
            timeText: formatTimeRange(detail.itinerary),
            planningStatusText: label(ITINERARY_STATUS, detail.itinerary.planningStatus),
            typeText: label(ITINERARY_TYPE, detail.itinerary.itineraryType),
            summaryText: itinerarySummary(detail.itinerary)
          },
          relatedPolls: (detail.relatedPolls || []).map((item) => ({
            ...item, statusText: label(POLL_STATUS, item.status), resultText: label(POLL_RESULT_STATUS, item.resultApplyStatus)
          }))
        }
      });
    } catch (error) { this.setData({ errorMessage: error.message || '加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  edit() {
    this.closeActionMenu();
    wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}&itineraryId=${this.data.itineraryId}` });
  },
  newPoll() {
    this.closeActionMenu();
    wx.navigateTo({ url: `/pages/poll-create/index?activityId=${this.data.activityId}&purpose=UPDATE_ITINERARY&targetItineraryId=${this.data.itineraryId}` });
  },
  goPoll(event) { wx.navigateTo({ url: `/pages/poll-detail/index?activityId=${this.data.activityId}&pollId=${event.currentTarget.dataset.id}` }); },
  toggleActionMenu() { this.setData({ actionMenuVisible: !this.data.actionMenuVisible }); },
  closeActionMenu() { this.setData({ actionMenuVisible: false }); },
  stopActionMenu() {},
  cancel() {
    this.closeActionMenu();
    wx.showModal({ title: '取消行程', content: '取消后仍会保留历史记录。', success: async (result) => {
      if (!result.confirm) return;
      try { await cancelItinerary(this.data.activityId, this.data.itineraryId); this.load(); }
      catch (error) { wx.showToast({ title: error.message || '取消失败', icon: 'none' }); }
    }});
  },
  restore() {
    this.closeActionMenu();
    wx.showModal({ title: '恢复行程', content: '恢复后行程会重新显示在活动安排中。', success: async (result) => {
      if (!result.confirm) return;
      try { await restoreItinerary(this.data.activityId, this.data.itineraryId); this.load(); }
      catch (error) { wx.showToast({ title: error.message || '恢复失败', icon: 'none' }); }
    }});
  },
  remove() {
    this.closeActionMenu();
    wx.showModal({ title: '删除行程', content: '删除后无法恢复。存在未完成关联投票时不能删除。', confirmText: '删除', confirmColor: '#D94C4C', success: async (result) => {
      if (!result.confirm) return;
      try {
        await deleteItinerary(this.data.activityId, this.data.itineraryId);
        wx.showToast({ title: '已删除', icon: 'success' });
        setTimeout(() => wx.navigateBack(), 350);
      } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }); }
    }});
  }
});
