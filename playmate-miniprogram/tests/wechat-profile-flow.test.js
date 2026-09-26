const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const auth = require('../services/auth');
const userService = require('../services/user');
const originalIsLoggedIn = auth.isLoggedIn;
const originalGetCurrentUser = userService.getCurrentUser;
const originalUpdateCurrentUserProfile = userService.updateCurrentUserProfile;
let savedProfile;

auth.isLoggedIn = () => true;
userService.getCurrentUser = () => Promise.resolve({
  nickname: '初始昵称',
  avatarUrl: 'https://example.com/old.png'
});
userService.updateCurrentUserProfile = (profile) => {
  savedProfile = profile;
  return Promise.resolve();
};

const navigation = [];
global.wx = {
  redirectTo(options) { navigation.push({ type: 'redirectTo', ...options }); },
  switchTab(options) { navigation.push({ type: 'switchTab', ...options }); },
  showToast() {}
};
let profilePage;
global.Page = (definition) => { profilePage = definition; };
require('../pages/wechat-profile/index');

function createPage() {
  return {
    ...profilePage,
    data: JSON.parse(JSON.stringify(profilePage.data)),
    setData(values) {
      Object.entries(values).forEach(([key, value]) => {
        if (!key.includes('.')) {
          this.data[key] = value;
          return;
        }
        const parts = key.split('.');
        let target = this.data;
        parts.slice(0, -1).forEach((part) => { target = target[part]; });
        target[parts[parts.length - 1]] = value;
      });
    }
  };
}

function collectRuntimeFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      return entry.name === 'tests' ? [] : collectRuntimeFiles(fullPath);
    }
    return ['.js', '.wxml', '.wxss', '.json', '.md'].includes(path.extname(entry.name)) ? [fullPath] : [];
  });
}

async function run() {
  const page = createPage();
  await page.loadUser();
  assert.equal(page.data.form.nickname, '初始昵称');
  assert.equal(page.data.form.avatarUrl, 'https://example.com/old.png');

  page.data.form = {
    nickname: ' 新昵称 ',
    avatarUrl: 'https://example.com/new.png'
  };
  page.data.redirect = '/pages/activity-invite/index?code=ABCD';
  const originalSetTimeout = global.setTimeout;
  global.setTimeout = (callback) => { callback(); return 0; };
  await page.saveProfile();
  global.setTimeout = originalSetTimeout;
  assert.deepEqual(savedProfile, {
    nickname: '新昵称',
    avatarUrl: 'https://example.com/new.png'
  });
  assert.deepEqual(navigation.pop(), {
    type: 'redirectTo',
    url: '/pages/activity-invite/index?code=ABCD'
  });

  page.data.redirect = '/pages/activity-invite/index?code=SKIP';
  page.skip();
  assert.deepEqual(navigation.pop(), {
    type: 'redirectTo',
    url: '/pages/activity-invite/index?code=SKIP'
  });

  page.data.redirect = '';
  page.skip();
  assert.deepEqual(navigation.pop(), {
    type: 'switchTab',
    url: '/pages/activity-list/index'
  });

  const source = collectRuntimeFiles(path.join(__dirname, '..'))
    .map((file) => fs.readFileSync(file, 'utf8'))
    .join('\n');
  const unsupportedTerms = [
    ['get', 'PhoneNumber'].join(''),
    ['bindget', 'phonenumber'].join(''),
    ['微信', '手机号'].join(''),
    ['手机号', '授权'].join('')
  ];
  unsupportedTerms.forEach((term) => assert.equal(source.includes(term), false, `${term} should be absent`));

  const accountLoginSource = fs.readFileSync(path.join(__dirname, '../pages/account-login/index.js'), 'utf8');
  const authSource = fs.readFileSync(path.join(__dirname, '../services/auth.js'), 'utf8');
  assert.equal(accountLoginSource.includes('请填写手机号或邮箱'), true);
  assert.equal(authSource.includes('function accountLogin'), true);
  assert.equal(authSource.includes('function accountRegister'), true);

  auth.isLoggedIn = originalIsLoggedIn;
  userService.getCurrentUser = originalGetCurrentUser;
  userService.updateCurrentUserProfile = originalUpdateCurrentUserProfile;
  console.log('wechat-profile-flow.test.js passed');
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
