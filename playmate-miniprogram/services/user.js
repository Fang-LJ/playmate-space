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

function updateMyAccount(data) {
  return request({
    url: '/api/users/me/account',
    method: 'PUT',
    data
  });
}

function bindWechatPhone(code) {
  return request({
    url: '/api/users/me/wechat-phone',
    method: 'POST',
    data: { code }
  });
}

module.exports = {
  getCurrentUser,
  updateCurrentUserProfile,
  updateMyAccount,
  bindWechatPhone
};
