const ACCOUNT_PROTECTION_NOTICE_KEY = 'PLAYMATE_ACCOUNT_PROTECTION_NOTICE';
const WECHAT_PROFILE_SUGGESTION_KEY = 'PLAYMATE_WECHAT_PROFILE_SUGGESTION';

function normalizeRedirect(redirect) {
  if (typeof redirect !== 'string') {
    return '';
  }
  const target = redirect.trim();
  return target.startsWith('/pages/') ? target : '';
}

function resolvePostLoginTarget({ redirect, defaultPath = '/pages/activity-list/index' } = {}) {
  return normalizeRedirect(redirect) || defaultPath;
}

function resolveStorage(storage) {
  if (storage) {
    return storage;
  }
  if (typeof wx !== 'undefined') {
    return {
      get(key) {
        return wx.getStorageSync(key);
      },
      set(key, value) {
        wx.setStorageSync(key, value);
      },
      remove(key) {
        wx.removeStorageSync(key);
      }
    };
  }
  return null;
}

function handleLoginSuccess(loginResponse, options = {}) {
  const storage = resolveStorage(options.storage);
  const response = loginResponse || {};
  const userId = response.userId || '';
  const accountProtected = Boolean(response.accountProtected);
  const profileComplete = Boolean(response.profileComplete);

  if (storage && userId) {
    storage.set(ACCOUNT_PROTECTION_NOTICE_KEY, {
      userId,
      show: !accountProtected
    });
    if (response.loginType === 'WECHAT_MINIPROGRAM' && response.isNewUser && !profileComplete) {
      storage.set(WECHAT_PROFILE_SUGGESTION_KEY, { userId, show: true });
    }
  }

  return resolvePostLoginTarget({
    redirect: options.redirect,
    defaultPath: options.defaultPath || '/pages/activity-list/index'
  });
}

function getWechatProfileSuggestion(userId, storage) {
  const currentStorage = resolveStorage(storage);
  const value = currentStorage ? currentStorage.get(WECHAT_PROFILE_SUGGESTION_KEY) : null;
  return Boolean(value && value.show && String(value.userId) === String(userId));
}

function clearWechatProfileSuggestion(storage) {
  const currentStorage = resolveStorage(storage);
  if (currentStorage) {
    currentStorage.remove(WECHAT_PROFILE_SUGGESTION_KEY);
  }
}

module.exports = {
  resolvePostLoginTarget,
  handleLoginSuccess,
  getWechatProfileSuggestion,
  clearWechatProfileSuggestion
};
