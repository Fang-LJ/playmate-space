const photo = require('../../services/photo');
const { choosePhotos, uploadPhoto } = require('../../services/file');
const ui = require('../../utils/photo-ui');
const { getActivityDetail } = require('../../services/activity');

function displayPhoto(item) {
  return { ...item, avatarText: (item.uploaderNickname || '玩').slice(0, 1), statusMeta: ui.photoStatus(item.auditStatus, item.visibilityStatus), timeText: ui.formatPhotoTime(item.createTime), statusDescription: ui.uploadTaskState(item).statusDescription };
}

Page({
  data: { activityId: '', activityStatus: '', canUploadPhoto: true, loading: true, loadingMore: false, error: '', summary: null, items: [], page: 1, hasMore: true, scope: 'ALL', sort: 'LATEST', layout: 3, toolVisible: false, draftScope: 'ALL', draftSort: 'LATEST', draftLayout: 3, previewVisible: false, previewPhoto: null, uploadTasks: [], uploadSummary: null, uploadVisible: false },
  onLoad(options) {
    const preferences = ui.loadPreferences();
    this.initialPhotoId = options.photoId || ''; this.initialAction = options.action || ''; this.initialPreviewTried = false; this.initialActionConsumed = false;
    this.setData({ activityId: options.activityId || '', ...preferences, draftScope: preferences.scope, draftSort: preferences.sort, draftLayout: preferences.layout });
  },
  async onShow() {
    if (!this.data.activityId) return;
    await this.refresh();
    await this.openInitialPreview();
    if (this.initialAction === 'upload' && !this.initialActionConsumed && this.data.canUploadPhoto) { this.initialActionConsumed = true; this.chooseAndUpload(); }
  },
  onHide() { this.stopPolling(); }, onUnload() { this.stopPolling(); },
  async refresh(options = {}) {
    if (this.refreshing) return;
    const silent = Boolean(options.silent); this.refreshing = true;
    if (!silent) this.setData({ loading: true, error: '', page: 1, hasMore: true });
    try {
      const [activity, summary, response] = await Promise.all([getActivityDetail(this.data.activityId), photo.getPhotoSummary(this.data.activityId), this.fetchPage(1)]);
      const items = (response.items || []).map(displayPhoto);
      this.setData({ activityStatus: activity.status, canUploadPhoto: activity.status !== 'CANCELED', summary, items, page: Number(response.page || 1), hasMore: items.length < Number(response.total || 0), error: '' });
      await this.syncUploadTasks();
      this.updatePolling();
    } catch (error) { if (!silent) this.setData({ error: error.message || '照片加载失败' }); }
    finally { this.refreshing = false; if (!silent) this.setData({ loading: false }); wx.stopPullDownRefresh(); }
  },
  onPullDownRefresh() { this.refresh(); },
  fetchPage(page) { return photo.getPhotos(this.data.activityId, { scope: this.data.scope, sort: this.data.sort, page, pageSize: 30 }); },
  async openInitialPreview() {
    if (!this.initialPhotoId || this.initialPreviewTried) return;
    this.initialPreviewTried = true;
    try { const item = displayPhoto(await photo.getPhotoDetail(this.data.activityId, this.initialPhotoId)); this.setData({ previewPhoto: item, previewVisible: true }); }
    catch (error) { /* The selected scope stays untouched; enter the wall normally when access has changed. */ }
  },
  async onReachBottom() {
    if (this.data.loading || this.data.loadingMore || !this.data.hasMore) return;
    this.setData({ loadingMore: true });
    try { const response = await this.fetchPage(this.data.page + 1); const items = ui.appendPage(this.data.items, response).map(displayPhoto); this.setData({ items, page: Number(response.page || this.data.page + 1), hasMore: items.length < Number(response.total || 0) }); this.updatePolling(); }
    catch (error) { wx.showToast({ title: error.message || '加载失败，点击重试', icon: 'none' }); this.setData({ error: '加载更多失败，点击重试' }); }
    finally { this.setData({ loadingMore: false }); }
  },
  retry() { this.refresh(); },
  openTools() { this.setData({ toolVisible: true, draftScope: this.data.scope, draftSort: this.data.sort, draftLayout: this.data.layout }); }, closeTools() { this.setData({ toolVisible: false }); },
  pickScope(e) { this.setData({ draftScope: e.currentTarget.dataset.value }); }, pickSort(e) { this.setData({ draftSort: e.currentTarget.dataset.value }); }, pickLayout(e) { this.setData({ draftLayout: Number(e.currentTarget.dataset.value) }); },
  resetTools() { this.setData({ draftScope: 'ALL', draftSort: 'LATEST', draftLayout: 3 }); },
  applyTools() { const next = ui.savePreferences({ scope: this.data.draftScope, sort: this.data.draftSort, layout: this.data.draftLayout }); this.setData({ ...next, toolVisible: false }); this.refresh(); },
  openMineUploads() { this.setData({ scope: 'MINE', draftScope: 'MINE' }); this.refresh(); },
  openPreview(e) { const current = this.data.items.find(item => String(item.photoId) === String(e.currentTarget.dataset.id)); if (current) this.setData({ previewPhoto: current, previewVisible: true }); }, closePreview() { this.setData({ previewVisible: false, previewPhoto: null }); }, stopPreviewClose() {},
  async toggleLike() {
    const before = this.data.previewPhoto; if (!ui.isLikable(before)) return;
    const desired = !before.likedByMe; const next = ui.optimisticLike(before, desired); this.applyPhotoPatch(before.photoId, next);
    try { desired ? await photo.likePhoto(this.data.activityId, before.photoId) : await photo.unlikePhoto(this.data.activityId, before.photoId); }
    catch (error) { this.applyPhotoPatch(before.photoId, before); wx.showToast({ title: error.message || '操作失败', icon: 'none' }); }
  },
  applyPhotoPatch(photoId, patch) { const current = this.data.previewPhoto; this.setData({ items: ui.patchPhoto(this.data.items, photoId, patch), previewPhoto: current && String(current.photoId) === String(photoId) ? { ...current, ...patch } : current }); },
  goDetail() { const item = this.data.previewPhoto; if (item) wx.navigateTo({ url: `/pages/photo-detail/index?activityId=${this.data.activityId}&photoId=${item.photoId}` }); },
  confirmDelete() {
    const item = this.data.previewPhoto; if (!item || !item.canDelete) return;
    wx.showModal({ title: '删除这张照片？', content: '删除后将从活动照片墙中移除，此操作无法撤销。', confirmColor: '#D94C4C', success: async result => { if (!result.confirm) return; try { await photo.deletePhoto(this.data.activityId, item.photoId); this.setData({ items: this.data.items.filter(row => String(row.photoId) !== String(item.photoId)) }); this.closePreview(); await this.refresh({ silent: true }); wx.showToast({ title: '已删除', icon: 'success' }); } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }); } } });
  },
  async chooseAndUpload() {
    if (!this.data.canUploadPhoto) return;
    try { const paths = await choosePhotos(); const tasks = paths.map((path, index) => ({ id: `${Date.now()}-${index}`, path, status: 'WAITING', statusText: '等待上传', statusTone: 'pending', statusDescription: '' })); this.setData({ uploadTasks: tasks, uploadVisible: true }); this.updateUploadSummary(); await this.uploadBatch(tasks); }
    catch (error) { if (error.code !== 'CHOOSE_IMAGE_ERROR') wx.showToast({ title: error.message || '选择照片失败', icon: 'none' }); }
  },
  async uploadBatch(tasks) {
    const finished = []; let failed = false;
    for (const task of tasks) { this.updateTask(task.id, { status: 'UPLOADING', statusText: '上传中', statusTone: 'pending' }); try { const result = await uploadPhoto(task.path); finished.push(result.fileId); this.updateTask(task.id, { status: 'UPLOADED', statusText: '等待绑定', statusTone: 'pending', fileId: result.fileId }); } catch (error) { failed = true; this.updateTask(task.id, { status: 'FAILED', statusText: error.message || '上传失败', statusTone: 'failed' }); } }
    if (failed) { wx.showToast({ title: `${finished.length} / ${tasks.length} 上传成功，请重新选择失败照片`, icon: 'none' }); return; }
    try { const created = await photo.createPhotos(this.data.activityId, finished); (created || []).forEach((item, index) => this.updateTask(tasks[index].id, { status: 'PENDING', statusText: '审核中', statusTone: 'pending', statusDescription: '审核通过后将自动展示', photoId: item.photoId })); wx.showToast({ title: '已提交审核', icon: 'success' }); await this.refresh({ silent: true }); }
    catch (error) { tasks.forEach(task => this.updateTask(task.id, { status: 'FAILED', statusText: '绑定失败，稍后可重新上传', statusTone: 'failed' })); wx.showToast({ title: error.message || '绑定照片失败', icon: 'none' }); }
  },
  updateTask(id, patch) { this.setData({ uploadTasks: (this.data.uploadTasks || []).map(item => item.id === id ? { ...item, ...patch } : item) }); this.updateUploadSummary(); },
  updateUploadSummary() { this.setData({ uploadSummary: ui.batchSummary(this.data.uploadTasks) }); }, closeUpload() { this.setData({ uploadVisible: false }); },
  async syncUploadTasks() {
    const tasks = (this.data.uploadTasks || []).filter(task => task.photoId);
    if (!tasks.length) return;
    const updates = await Promise.all(tasks.map(async task => { try { const item = await photo.getPhotoDetail(this.data.activityId, task.photoId); return { id: task.id, ...ui.uploadTaskState(item) }; } catch (error) { return null; } }));
    updates.filter(Boolean).forEach(update => this.updateTask(update.id, update));
  },
  updatePolling() {
    const taskPending = (this.data.uploadTasks || []).some(task => ['PENDING', 'REVIEWING', 'UPLOADED', 'UPLOADING'].includes(task.status));
    const needsPolling = taskPending || (this.data.summary && Number(this.data.summary.pendingAuditCount || 0) > 0) || this.data.items.some(item => ['PENDING', 'REVIEWING'].includes(item.auditStatus) || item.visibilityStatus === 'REVIEWING');
    if (needsPolling && !this.pollTimer) this.pollTimer = setInterval(() => this.refresh({ silent: true }), 8000);
    if (!needsPolling) this.stopPolling();
  },
  stopPolling() { if (this.pollTimer) { clearInterval(this.pollTimer); this.pollTimer = null; } }
});
