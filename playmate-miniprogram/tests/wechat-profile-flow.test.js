const assert = require('node:assert/strict');
const { MOCK_USERS, getMockPhoneCodeByKey } = require('../services/auth');
const { handleLoginSuccess, shouldPromptWechatProfile, markWechatProfilePrompted } = require('../utils/login-flow');
const { maskPhone, resolvePhoneAuthorizationCode } = require('../utils/wechat-profile-flow');

function createStorage() {
  const values = new Map();
  return {
    get(key) { return values.get(key); },
    set(key, value) { values.set(key, value); },
    remove(key) { values.delete(key); }
  };
}

assert.equal(MOCK_USERS.length, 3);
assert.equal(getMockPhoneCodeByKey('A'), 'mock_phone_a');
assert.equal(getMockPhoneCodeByKey('B'), 'mock_phone_b');
assert.equal(getMockPhoneCodeByKey('C'), 'mock_phone_c');
assert.notEqual(getMockPhoneCodeByKey('A'), getMockPhoneCodeByKey('B'));
assert.equal(maskPhone('13800000001'), '138****0001');

const storage = createStorage();
const wechatNewUser = {
  userId: 100,
  loginType: 'WECHAT_MINIPROGRAM',
  isNewUser: true,
  accountProtected: false,
  profileComplete: false
};
assert.equal(shouldPromptWechatProfile(wechatNewUser, storage), true);
assert.equal(handleLoginSuccess(wechatNewUser, {
  storage,
  redirect: '/pages/activity-invite/index?code=ABCD'
}), '/pages/activity-invite/index?code=ABCD');
markWechatProfilePrompted(100, storage);
assert.equal(shouldPromptWechatProfile(wechatNewUser, storage), false);
assert.equal(shouldPromptWechatProfile({
  ...wechatNewUser,
  loginType: 'ACCOUNT'
}, storage), false);

assert.deepEqual(resolvePhoneAuthorizationCode({
  errMsg: 'getPhoneNumber:ok',
  code: 'real_wechat_code_xxx'
}, 'mock_phone_a', { preferMock: true }), {
  cancelled: false,
  code: 'mock_phone_a',
  isMock: true
});
assert.deepEqual(resolvePhoneAuthorizationCode({
  errMsg: 'getPhoneNumber:ok',
  code: 'real_wechat_code_xxx'
}, 'mock_phone_a', { preferMock: false }), {
  cancelled: false,
  code: 'real_wechat_code_xxx',
  isMock: false
});
assert.deepEqual(resolvePhoneAuthorizationCode({}, 'mock_phone_a'), {
  cancelled: false,
  code: 'mock_phone_a',
  isMock: true
});
assert.deepEqual(resolvePhoneAuthorizationCode({ errMsg: 'getPhoneNumber:fail user deny' }, 'mock_phone_a'), {
  cancelled: true,
  code: '',
  isMock: false
});

console.log('wechat-profile-flow.test.js passed');
