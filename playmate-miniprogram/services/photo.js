const { request } = require('../utils/request');

function base(activityId) { return `/api/activities/${activityId}/photos`; }
function getPhotoSummary(activityId) { return request({ url: `${base(activityId)}/summary` }); }
function getPhotos(activityId, options = {}) { return request({ url: base(activityId), data: { scope: options.scope || 'ALL', sort: options.sort || 'LATEST', page: options.page || 1, pageSize: options.pageSize || 30 } }); }
function getPhotoDetail(activityId, photoId) { return request({ url: `${base(activityId)}/${photoId}` }); }
function createPhotos(activityId, fileIds) { return request({ url: base(activityId), method: 'POST', data: { fileIds } }); }
function deletePhoto(activityId, photoId) { return request({ url: `${base(activityId)}/${photoId}`, method: 'DELETE' }); }
function likePhoto(activityId, photoId) { return request({ url: `${base(activityId)}/${photoId}/like`, method: 'POST' }); }
function unlikePhoto(activityId, photoId) { return request({ url: `${base(activityId)}/${photoId}/like`, method: 'DELETE' }); }
function reportPhoto(activityId, photoId, reasonCode) { return request({ url: `${base(activityId)}/${photoId}/reports`, method: 'POST', data: { reasonCode } }); }
function getOriginalUrl(activityId, photoId) { return request({ url: `${base(activityId)}/${photoId}/original-url` }); }

module.exports = { getPhotoSummary, getPhotos, getPhotoDetail, createPhotos, deletePhoto, likePhoto, unlikePhoto, reportPhoto, getOriginalUrl };
