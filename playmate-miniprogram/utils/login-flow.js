const ACCOUNT_PROTECTION_NOTICE_KEY = 'PLAYMATE_ACCOUNT_PROTECTION_NOTICE';

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
  if (storage && userId) {
    storage.set(ACCOUNT_PROTECTION_NOTICE_KEY, {
      userId,
      show: !accountProtected
    });
  }

  return resolvePostLoginTarget({
    redirect: options.redirect,
    defaultPath: options.defaultPath || '/pages/activity-list/index'
  });
}

function shouldCompleteWechatProfile(loginResponse) {
  const response = loginResponse || {};
  return response.loginType === 'WECHAT_MINIPROGRAM'
    && response.isNewUser === true
    && response.profileComplete === false;
}

module.exports = {
  resolvePostLoginTarget,
  handleLoginSuccess,
  shouldCompleteWechatProfile
};
