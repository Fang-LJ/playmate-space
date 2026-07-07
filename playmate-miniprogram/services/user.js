const { request } = require('../utils/request');

function getCurrentUser() {
  return request({
    url: '/api/users/me'
  });
}

function updateCurrentUserProfile(data) {
  return request({
    url: '/api/users/me',
    method: 'PUT',
    data
  });
}

module.exports = {
  getCurrentUser,
  updateCurrentUserProfile
};
