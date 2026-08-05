const { getItineraries, getItineraryTypeMetadata, deleteItinerary: removeItinerary } = require('../../services/itinerary');
const { getActivityDetail } = require('../../services/activity');
const { getCurrentUser } = require('../../services/user');
const { dateGroupMeta } = require('../../utils/p1-display');
const { buildCardViewModel, normalizeMetadata } = require('../../utils/itinerary-ui');

Page({
  data: { activityId: '', loading: true, errorMessage: '', items: [], readOnly: false, typeMetadata: [], isActivityCreator: false, currentUserId: '', openItineraryId: null },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load(forceMetadata = false) {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [items, activity, metadata, currentUser] = await Promise.all([
        getItineraries(this.data.activityId, true),
        getActivityDetail(this.data.activityId),
        getItineraryTypeMetadata(forceMetadata),
        getCurrentUser()
      ]);
      const typeMetadata = normalizeMetadata(metadata);
      const currentUserId = String(currentUser.userId);
      const isActivityCreator = activity.currentUserRole === 'CREATOR';
      this.setData({
        items: this.group((items || []).map((item) => ({
          ...item,
          canEdit: !['ENDED', 'CANCELED'].includes(activity.status) && item.planningStatus !== 'CANCELED'
            && (isActivityCreator || String(item.createdBy) === currentUserId),
          canDelete: !['ENDED', 'CANCELED'].includes(activity.status)
            && (isActivityCreator || String(item.createdBy) === currentUserId)
        })), typeMetadata),
        readOnly: ['ENDED', 'CANCELED'].includes(activity.status),
        typeMetadata,
        isActivityCreator,
        currentUserId,
        openItineraryId: null
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
  retry() { this.load(true); }
});
