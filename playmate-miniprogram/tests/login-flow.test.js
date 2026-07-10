const assert = require('node:assert/strict');
const {
  handleLoginSuccess,
  resolvePostLoginTarget,
  shouldPromptWechatProfile,
  markWechatProfilePrompted
} = require('../utils/login-flow');
const { normalizeShareCode, buildInvitePath } = require('../utils/share-code');

function createStorage() {
  const values = new Map();
  return {
    get(key) { return values.get(key); },
    set(key, value) { values.set(key, value); },
    remove(key) { values.delete(key); }
  };
}

const storage = createStorage();

assert.equal(handleLoginSuccess({
  userId: 1,
  loginType: 'WECHAT_MINIPROGRAM',
  isNewUser: true,
  accountProtected: false,
  profileComplete: false
}, { storage }), '/pages/activity-list/index');

const newWechatUser = {
  userId: 10,
  loginType: 'WECHAT_MINIPROGRAM',
  isNewUser: true,
  accountProtected: false,
  profileComplete: false
};
assert.equal(shouldPromptWechatProfile(newWechatUser, storage), true);
markWechatProfilePrompted(10, storage);
assert.equal(shouldPromptWechatProfile(newWechatUser, storage), false);
assert.equal(shouldPromptWechatProfile({ ...newWechatUser, loginType: 'ACCOUNT' }, storage), false);

assert.equal(handleLoginSuccess({
  userId: 1,
  loginType: 'WECHAT_MINIPROGRAM',
  isNewUser: true,
  accountProtected: false,
  profileComplete: false
}, {
  storage,
  redirect: '/pages/activity-invite/index?code=ABCD1234'
}), '/pages/activity-invite/index?code=ABCD1234');

assert.equal(handleLoginSuccess({
  userId: 2,
  loginType: 'ACCOUNT',
  isNewUser: false,
  accountProtected: true,
  profileComplete: false
}, { storage }), '/pages/activity-list/index');

assert.equal(handleLoginSuccess({
  userId: 3,
  loginType: 'ACCOUNT',
  isNewUser: false,
  accountProtected: true,
  profileComplete: true
}, {
  storage,
  redirect: '/pages/activity-detail/index?activityId=12'
}), '/pages/activity-detail/index?activityId=12');

assert.equal(resolvePostLoginTarget({ redirect: 'https://example.com' }), '/pages/activity-list/index');
assert.equal(normalizeShareCode('  abcd1234  '), 'ABCD1234');
assert.equal(normalizeShareCode('   '), '');
assert.equal(buildInvitePath(' abcd1234 '), '/pages/activity-invite/index?code=ABCD1234');
assert.equal(buildInvitePath('   '), '');

console.log('login-flow.test.js passed');
