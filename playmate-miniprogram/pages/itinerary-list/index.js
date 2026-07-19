const { getItineraries } = require('../../services/itinerary');
const { getActivityDetail } = require('../../services/activity');
const { ITINERARY_STATUS, formatTimeRange, label } = require('../../utils/p1-display');

Page({
  data: { activityId: '', loading: true, errorMessage: '', items: [], readOnly: false },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [items, activity] = await Promise.all([getItineraries(this.data.activityId, true), getActivityDetail(this.data.activityId)]);
      this.setData({ items: this.group(items || []), readOnly: ['ENDED', 'CANCELED'].includes(activity.status) });
    } catch (error) { this.setData({ errorMessage: error.message || '行程加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  group(items) {
    const groups = {};
    items.forEach((item) => {
      (groups[item.itineraryDate] || (groups[item.itineraryDate] = [])).push({
        ...item,
        timeText: formatTimeRange(item),
        statusText: label(ITINERARY_STATUS, item.planningStatus),
        timeTextStatus: { UPCOMING: '未开始', IN_PROGRESS: '进行中', FINISHED: '已完成', CANCELED: '已取消' }[item.timeStatus] || ''
      });
    });
    return Object.keys(groups).sort().map((date) => ({ date, items: groups[date] }));
  },
  goCreate() { wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}` }); },
  goDetail(event) { wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${this.data.activityId}&itineraryId=${event.currentTarget.dataset.id}` }); },
  retry() { this.load(); }
});
