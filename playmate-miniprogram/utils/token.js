const TOKEN_KEY = 'PLAYMATE_SPACE_TOKEN';

function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || '';
}

function setToken(token) {
  if (!token) {
    clearToken();
    return;
  }
  wx.setStorageSync(TOKEN_KEY, token);
}

function clearToken() {
  wx.removeStorageSync(TOKEN_KEY);
}

module.exports = {
  getToken,
  setToken,
  clearToken
};
