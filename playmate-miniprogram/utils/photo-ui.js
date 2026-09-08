const SCOPE_LABEL = { ALL: '全部', MINE: '我上传', LIKED: '我点赞' };
const SORT_LABEL = { LATEST: '最新优先', EARLIEST: '最早优先', MOST_LIKED: '获赞最多' };
const STATUS_META = {
  PENDING: { text: '审核中', className: 'pending' }, APPROVED: { text: '已通过', className: 'approved' },
  REJECTED: { text: '未通过审核', className: 'rejected' }, REVIEWING: { text: '复核中', className: 'reviewing' }
};
const PREFERENCE_KEY = 'photo-wall-preferences';

function normalizeOptions(options = {}) { return { scope: SCOPE_LABEL[options.scope] ? options.scope : 'ALL', sort: SORT_LABEL[options.sort] ? options.sort : 'LATEST', layout: Number(options.layout) === 2 ? 2 : 3 }; }
function loadPreferences(storage = wx) { return normalizeOptions(storage.getStorageSync(PREFERENCE_KEY) || {}); }
function savePreferences(value, storage = wx) { const options = normalizeOptions(value); storage.setStorageSync(PREFERENCE_KEY, options); return options; }
function appendPage(existing, response) { const incoming = response && response.items ? response.items : []; const ids = new Set((existing || []).map(item => String(item.photoId))); return (existing || []).concat(incoming.filter(item => !ids.has(String(item.photoId)))); }
function photoStatus(status, visibilityStatus) { if (visibilityStatus === 'REVIEWING') return STATUS_META.REVIEWING; return STATUS_META[status] || STATUS_META.PENDING; }
function uploadTaskState(photo) {
  const meta = photoStatus(photo.auditStatus, photo.visibilityStatus);
  const details = { PENDING: '审核通过后将自动展示', DONE: '已通过审核', REJECTED: '未通过内容安全审核', REVIEWING: '正在重新审核，暂不展示' };
  const status = meta.className === 'approved' ? 'DONE' : meta.className.toUpperCase();
  return { status, statusText: meta.text, statusTone: meta.className, statusDescription: details[status] || '' };
}
function isLikable(photo) { return Boolean(photo) && photo.auditStatus === 'APPROVED' && photo.visibilityStatus === 'NORMAL'; }
function nextSwiperIndex(current, total, direction) { if (!total) return 0; return (Number(current || 0) + (direction || 0) + total) % total; }
function formatFileSize(bytes) { const value = Number(bytes || 0); if (value < 1024 * 1024) return `${Math.max(0, Math.round(value / 1024))} KB`; return `${(value / 1024 / 1024).toFixed(value >= 10 * 1024 * 1024 ? 0 : 1)} MB`; }
function formatPhotoTime(value) { if (!value) return '刚刚上传'; const date = new Date(String(value).replace('T', ' ').replace(/-/g, '/')); if (Number.isNaN(date.getTime())) return String(value).slice(0, 16); const pad = number => String(number).padStart(2, '0'); return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`; }
function formatDimensions(width, height) { return width && height ? `${width} × ${height}` : '尺寸信息暂不可用'; }
function patchPhoto(items, photoId, patch) { return (items || []).map(item => String(item.photoId) === String(photoId) ? { ...item, ...patch } : item); }
function optimisticLike(photo, desiredLiked) { const liked = Boolean(photo && photo.likedByMe); if (!photo || liked === desiredLiked) return photo; return { ...photo, likedByMe: desiredLiked, likeCount: Math.max(0, Number(photo.likeCount || 0) + (desiredLiked ? 1 : -1)) }; }
function batchSummary(tasks) { const list = tasks || []; return { total: list.length, waiting: list.filter(item => item.status === 'WAITING').length, uploading: list.filter(item => item.status === 'UPLOADING').length, failed: list.filter(item => item.status === 'FAILED').length, pending: list.filter(item => item.status === 'PENDING').length, reviewing: list.filter(item => item.status === 'REVIEWING').length, rejected: list.filter(item => item.status === 'REJECTED').length, done: list.filter(item => item.status === 'DONE').length }; }

module.exports = { SCOPE_LABEL, SORT_LABEL, STATUS_META, normalizeOptions, loadPreferences, savePreferences, appendPage, photoStatus, uploadTaskState, isLikable, nextSwiperIndex, formatFileSize, formatPhotoTime, formatDimensions, patchPhoto, optimisticLike, batchSummary };
