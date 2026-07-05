const { request } = require('../utils/request');
const { getToken } = require('../utils/token');

function getActivityInviteInfo(shareCode) {
  const token = getToken();
  return request({
    url: `/api/activity-invites/${shareCode}`,
    requireAuth: false,
    header: token ? {
      Authorization: `Bearer ${token}`
    } : {}
  });
}

function joinActivity(shareCode) {
  return request({
    url: `/api/activity-invites/${shareCode}/join`,
    method: 'POST'
  });
}

module.exports = {
  getActivityInviteInfo,
  joinActivity
};
