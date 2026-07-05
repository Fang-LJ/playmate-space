const { request } = require('../utils/request');

function getActivityMembers(activityId) {
  return request({
    url: `/api/activities/${activityId}/members`
  });
}

function updateMyActivityNickname(activityId, nickname) {
  return request({
    url: `/api/activities/${activityId}/members/me/nickname`,
    method: 'PUT',
    data: {
      nickname
    }
  });
}

function removeActivityMember(activityId, memberId) {
  return request({
    url: `/api/activities/${activityId}/members/${memberId}`,
    method: 'DELETE'
  });
}

module.exports = {
  getActivityMembers,
  updateMyActivityNickname,
  removeActivityMember
};
