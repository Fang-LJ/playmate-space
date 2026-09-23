const assert = require('node:assert/strict');

const config = require('../utils/config');
const requestModule = require('../utils/request');
const tokenModule = require('../utils/token');
const originalEnvironment = config.getActiveEnv;
const originalRequest = requestModule.request;
const originalSetToken = tokenModule.setToken;
let environment = 'prod';
let sentRequest;
let savedToken;
let wxLoginCalls = 0;

config.getActiveEnv = () => environment;
requestModule.request = (options) => {
  sentRequest = options;
  return Promise.resolve({ token: 'test-jwt' });
};
tokenModule.setToken = (value) => { savedToken = value; };
global.wx = {
  login({ success }) {
    wxLoginCalls += 1;
    success({ code: 'temporary-code' });
  },
  getStorageSync() { return 'B'; }
};

const auth = require('../services/auth');

async function run() {
  await auth.wxLogin();
  assert.equal(wxLoginCalls, 1);
  assert.equal(sentRequest.url, '/api/auth/wx-login');
  assert.deepEqual(sentRequest.data, { code: 'temporary-code' });
  assert.equal(savedToken, 'test-jwt');
  assert.equal(auth.getCurrentMockPhoneCode(), '');
  assert.equal(auth.getCurrentMockUser(), null);
  assert.equal(auth.selectMockUser('A'), null);

  environment = 'local';
  await auth.wxLogin();
  assert.equal(wxLoginCalls, 1);
  assert.equal(sentRequest.data.mockOpenid, 'mock_user_b');
  assert.equal(auth.getCurrentMockPhoneCode(), 'mock_phone_b');

  config.getActiveEnv = originalEnvironment;
  requestModule.request = originalRequest;
  tokenModule.setToken = originalSetToken;
  console.log('auth-login.test.js passed');
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
