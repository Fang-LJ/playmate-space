const { getItineraries, getItineraryTypeMetadata } = require('../../services/itinerary');
const { getActivityDetail } = require('../../services/activity');
const { dateGroupMeta } = require('../../utils/p1-display');
const { buildCardViewModel, normalizeMetadata } = require('../../utils/itinerary-ui');

Page({
  data: { activityId: '', loading: true, errorMessage: '', items: [], readOnly: false, typeMetadata: [] },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load(forceMetadata = false) {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [items, activity, metadata] = await Promise.all([
        getItineraries(this.data.activityId, true),
        getActivityDetail(this.data.activityId),
        getItineraryTypeMetadata(forceMetadata)
      ]);
      const typeMetadata = normalizeMetadata(metadata);
      this.setData({
        items: this.group(items || [], typeMetadata),
        readOnly: ['ENDED', 'CANCELED'].includes(activity.status),
        typeMetadata
      });
    } catch (error) { this.setData({ errorMessage: error.message || '行程加载失败' }); }
    finally { this.setData({ loading: false }); }
  },
  group(items, metadata) {
    const groups = {};
    items.forEach((item) => {
      (groups[item.itineraryDate] || (groups[item.itineraryDate] = [])).push(buildCardViewModel(item, metadata));
    });
    return Object.keys(groups).sort().map((date) => {
      const groupMeta = dateGroupMeta(date, groups[date].length);
      const parts = date.split('-').map(Number);
      const dateText = parts.length === 3 && parts.every(Number.isFinite)
        ? `${parts[1]}月${parts[2]}日 ${groupMeta.weekday}`
        : `${date} ${groupMeta.weekday}`.trim();
      return {
        ...groupMeta,
        dateText,
        count: groups[date].length,
        items: groups[date].sort((left, right) => String(left.startTime || '').localeCompare(String(right.startTime || '')))
      };
    });
  },
  goCreate() { wx.navigateTo({ url: `/pages/itinerary-edit/index?activityId=${this.data.activityId}` }); },
  goDetail(event) {
    wx.navigateTo({ url: `/pages/itinerary-detail/index?activityId=${this.data.activityId}&itineraryId=${event.detail.itineraryId}` });
  },
  retry() { this.load(true); }
});
