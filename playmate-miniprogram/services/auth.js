const { request } = require('../utils/request');
const { getToken, setToken, clearToken } = require('../utils/token');

const MOCK_OPENID_KEY = 'PLAYMATE_SPACE_MOCK_OPENID';

function getMockOpenid() {
  const existing = wx.getStorageSync(MOCK_OPENID_KEY);
  if (existing) {
    return existing;
  }

  const mockOpenid = `mock_${Date.now()}_${Math.random().toString(16).slice(2, 10)}`;
  wx.setStorageSync(MOCK_OPENID_KEY, mockOpenid);
  return mockOpenid;
}

function wxLogin() {
  return request({
    url: '/api/auth/wx-login',
    method: 'POST',
    requireAuth: false,
    data: {
      mockOpenid: getMockOpenid(),
      nickname: '玩伴用户',
      avatarUrl: ''
    }
  }).then((loginResult) => {
    setToken(loginResult.token);
    return loginResult;
  });
}

function getCurrentUser() {
  return request({
    url: '/api/users/me'
  });
}

function logout() {
  clearToken();
}

function isLoggedIn() {
  return Boolean(getToken());
}

module.exports = {
  wxLogin,
  getCurrentUser,
  logout,
  isLoggedIn
};
