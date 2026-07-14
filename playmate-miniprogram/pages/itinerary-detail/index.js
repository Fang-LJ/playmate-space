const { getItineraryDetail, cancelItinerary } = require('../../services/itinerary');
const { ITINERARY_STATUS, POLL_RESULT_STATUS, POLL_STATUS, formatTimeRange, label } = require('../../utils/p1-display');

Page({
  data: { activityId: '', itineraryId: '', detail: null, loading: true, errorMessage: '' },
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
            planningStatusText: label(ITINERARY_STATUS, detail.itinerary.planningStatus)
          },
          relatedPolls: (detail.relatedPolls || []).map((item) => ({
            ...item, statusText: label(POLL_STATUS, item.status), resultText: label(POLL_RESULT_STATUS, item.resultApplyStatus)
          }))
        }
      });
    } catch (error) { this.setData({ errorMessage: error.message || '加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  edit() { wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}&itineraryId=${this.data.itineraryId}` }); },
  newPoll() { wx.navigateTo({ url: `/pages/poll-create/index?activityId=${this.data.activityId}&purpose=UPDATE_ITINERARY&targetItineraryId=${this.data.itineraryId}` }); },
  goPoll(event) { wx.navigateTo({ url: `/pages/poll-detail/index?activityId=${this.data.activityId}&pollId=${event.currentTarget.dataset.id}` }); },
  cancel() {
    wx.showModal({ title: '取消行程', content: '取消后仍会保留历史记录。', success: async (result) => {
      if (!result.confirm) return;
      try { await cancelItinerary(this.data.activityId, this.data.itineraryId); this.load(); }
      catch (error) { wx.showToast({ title: error.message || '取消失败', icon: 'none' }); }
    }});
  }
});
