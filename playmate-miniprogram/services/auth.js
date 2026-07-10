const { request } = require('../utils/request');
const { getToken, setToken, clearToken } = require('../utils/token');

const MOCK_USER_KEY = 'PLAYMATE_SPACE_MOCK_USER';
const MOCK_USERS = [
  { key: 'A', mockOpenid: 'mock_user_a', nickname: '微信用户A', avatarUrl: '', phoneCode: 'mock_phone_a' },
  { key: 'B', mockOpenid: 'mock_user_b', nickname: '微信用户B', avatarUrl: '', phoneCode: 'mock_phone_b' },
  { key: 'C', mockOpenid: 'mock_user_c', nickname: '微信用户C', avatarUrl: '', phoneCode: 'mock_phone_c' }
];

function getCurrentMockUser() {
  const selectedKey = wx.getStorageSync(MOCK_USER_KEY) || 'A';
  return MOCK_USERS.find((user) => user.key === selectedKey) || MOCK_USERS[0];
}

function selectMockUser(key) {
  const mockUser = MOCK_USERS.find((user) => user.key === key);
  if (!mockUser) {
    return getCurrentMockUser();
  }
  wx.setStorageSync(MOCK_USER_KEY, mockUser.key);
  return mockUser;
}

function getMockPhoneCodeByKey(key) {
  const mockUser = MOCK_USERS.find((user) => user.key === key);
  return mockUser ? mockUser.phoneCode : '';
}

function getCurrentMockPhoneCode() {
  return getCurrentMockUser().phoneCode;
}

function wxLogin() {
  const mockUser = getCurrentMockUser();
  return request({
    url: '/api/auth/wx-login',
    method: 'POST',
    requireAuth: false,
    data: {
      mockOpenid: mockUser.mockOpenid,
      nickname: mockUser.nickname,
      avatarUrl: mockUser.avatarUrl
    }
  }).then((loginResult) => {
    setToken(loginResult.token);
    return loginResult;
  });
}

function accountLogin(data) {
  return request({
    url: '/api/auth/account-login',
    method: 'POST',
    requireAuth: false,
    data
  }).then((loginResult) => {
    setToken(loginResult.token);
    return loginResult;
  });
}

function accountRegister(data) {
  return request({
    url: '/api/auth/account-register',
    method: 'POST',
    requireAuth: false,
    data
  }).then((loginResult) => {
    setToken(loginResult.token);
    return loginResult;
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
  accountLogin,
  accountRegister,
  logout,
  isLoggedIn,
  getCurrentMockUser,
  selectMockUser,
  getMockPhoneCodeByKey,
  getCurrentMockPhoneCode,
  MOCK_USERS
};
