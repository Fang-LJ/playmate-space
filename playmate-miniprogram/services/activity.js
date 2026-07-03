const { request } = require('../utils/request');

function createActivity(data) {
  return request({
    url: '/api/activities',
    method: 'POST',
    data
  });
}

function getMyActivities() {
  return request({
    url: '/api/activities'
  });
}

function getActivityDetail(activityId) {
  return request({
    url: `/api/activities/${activityId}`
  });
}

function updateActivity(activityId, data) {
  return request({
    url: `/api/activities/${activityId}`,
    method: 'PUT',
    data
  });
}

function endActivity(activityId) {
  return request({
    url: `/api/activities/${activityId}/end`,
    method: 'POST'
  });
}

function cancelActivity(activityId) {
  return request({
    url: `/api/activities/${activityId}/cancel`,
    method: 'POST'
  });
}

module.exports = {
  createActivity,
  getMyActivities,
  getActivityDetail,
  updateActivity,
  endActivity,
  cancelActivity
};
