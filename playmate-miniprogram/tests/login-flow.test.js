const assert = require('node:assert/strict');
const {
  handleLoginSuccess,
  resolvePostLoginTarget,
  shouldCompleteWechatProfile
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
assert.equal(shouldCompleteWechatProfile(newWechatUser), true);
assert.equal(shouldCompleteWechatProfile({ ...newWechatUser, isNewUser: false }), false);
assert.equal(shouldCompleteWechatProfile({ ...newWechatUser, profileComplete: true }), false);
assert.equal(shouldCompleteWechatProfile({ ...newWechatUser, loginType: 'ACCOUNT' }), false);

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

const navigation = [];
global.wx = {
  getStorageSync() { return ''; },
  setStorageSync() {},
  removeStorageSync() {},
  redirectTo(options) { navigation.push({ type: 'redirectTo', ...options }); },
  switchTab(options) { navigation.push({ type: 'switchTab', ...options }); },
  showModal() { throw new Error('First login must not show a confirmation modal'); }
};
let loginPage;
global.Page = (definition) => { loginPage = definition; };
require('../pages/login/index');

const inviteTarget = '/pages/activity-invite/index?code=INVITE88';
loginPage.goAfterLogin.call({
  data: { redirect: inviteTarget },
  goRedirectTarget: loginPage.goRedirectTarget
}, newWechatUser);
assert.equal(navigation[0].type, 'redirectTo');
assert.equal(navigation[0].url.startsWith('/pages/wechat-profile/index?redirect='), true);
assert.equal(decodeURIComponent(navigation[0].url.split('redirect=')[1]), inviteTarget);

navigation.length = 0;
loginPage.goAfterLogin.call({
  data: { redirect: '' },
  goRedirectTarget: loginPage.goRedirectTarget
}, { ...newWechatUser, isNewUser: false });
assert.deepEqual(navigation[0], { type: 'switchTab', url: '/pages/activity-list/index' });

navigation.length = 0;
let accountLoginPage;
global.Page = (definition) => { accountLoginPage = definition; };
require('../pages/account-login/index');
accountLoginPage.goAfterLogin.call({
  data: { redirect: inviteTarget },
  goRedirectTarget: accountLoginPage.goRedirectTarget
}, newWechatUser);
assert.equal(decodeURIComponent(navigation[0].url.split('redirect=')[1]), inviteTarget);

console.log('login-flow.test.js passed');
