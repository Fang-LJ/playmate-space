const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const { createRequire } = require('node:module');
const { bookExpensePayload, bookExpenseView } = require('../utils/expense-context');

function page(file, mocks, wx = {}) {
  const full = path.resolve(__dirname, '../pages', file, 'index.js');
  let definition;
  vm.runInNewContext(fs.readFileSync(full, 'utf8'), {
    require: name => name in mocks ? mocks[name] : createRequire(full)(name),
    Page: data => { definition = data; }, wx, setTimeout: fn => fn(), console
  });
  const instance = { ...definition, data: structuredClone(definition.data), setData(patch, callback) {
    for (const [key, value] of Object.entries(patch)) {
      const parts = key.replace(/\[(\d+)\]/g, '.$1').split('.'); let obj = this.data;
      parts.slice(0, -1).forEach(part => { obj = obj[part]; }); obj[parts.at(-1)] = value;
    }
    if (callback) callback();
  } };
  return instance;
}
const plain = value => JSON.parse(JSON.stringify(value));

test('shared form adapter uses stable book member IDs and preserves monetary strings', () => {
  const view = bookExpenseView({ payerMemberId: 11, createdBy: 900, shares: [{ memberId: 12, shareAmount: '0.01', splitRatio: '1.0000' }] });
  const payload = bookExpensePayload({ payerUserId: view.payerUserId, amount: '0.01', shares: view.shares });
  assert.equal(payload.payerMemberId, 11);
  assert.deepEqual(payload.shares, [{ memberId: 12, shareAmount: '0.01', splitRatio: '1.0000' }]);
  assert.equal(payload.payerUserId, undefined);
});

test('editing historical expense does not automatically include later members; duplicate save blocked', async () => {
  let saved = 0, captured;
  let resolveSave;
  const context = {
    loadEditor: async () => ({ members: [{ userId: 11, nickname: '我' }, { userId: 12, nickname: '小明' }, { userId: 13, nickname: '后来加入' }], activity: { currentUserRole: 'CREATOR' }, currentUser: { userId: 11 } }),
    getExpense: async () => ({ title: '晚饭', category: 'FOOD', amount: '100.00', payerUserId: 11, expenseTime: '2026-09-08T18:00:00', splitMode: 'EQUAL', version: 2, shares: [{ userId: 11, shareAmount: '50' }, { userId: 12, shareAmount: '50' }] }),
    saveExpense: data => { saved++; captured = data; return new Promise(resolve => { resolveSave = resolve; }); }
  };
  const editor = page('expense-edit', { '../../utils/expense-context': { createExpenseContext: () => context } }, { showToast() {}, navigateBack() {} });
  await editor.onLoad({ bookId: '9', expenseId: '8' });
  assert.equal(editor.data.form.shares[2].checked, false);
  const first = editor.submit(); await editor.submit();
  assert.equal(saved, 1); assert.deepEqual(plain(captured.shares.map(s => s.userId)), [11, 12]);
  resolveSave(); await first;
  assert.equal(editor.data.saving, false);
});

test('failed or archived editor remains unsavable', async () => {
  let saved = false;
  const editor = page('expense-edit', { '../../utils/expense-context': { createExpenseContext: () => ({ loadEditor: async () => { throw new Error('账本已归档'); }, saveExpense: () => { saved = true; } }) } }, { showToast() {} });
  await editor.onLoad({ bookId: '9' }); await editor.submit();
  assert.equal(editor.data.ready, false); assert.equal(saved, false); assert.match(editor.data.loadError, /归档/);
});

test('claim refresh is read-only, rejected claim unlocks selection, approval opens book', async () => {
  let status = 'PENDING', joins = 0, destination = '';
  const invite = page('book-invite', {
    '../../services/auth': { isLoggedIn: () => true },
    '../../services/book': { invite: async () => ({ status: 'OPEN', name: '聚餐', members: [{ memberId: 12, nickname: '小明' }] }), joinStatus: async () => ({ status, bookId: 9 }), join: async () => { joins++; } }
  }, { redirectTo: ({ url }) => { destination = url; }, showToast() {} });
  invite.onLoad({ code: 'abc', memberId: '12' }); await invite.load();
  assert.equal(invite.data.pending, true);
  status = 'REJECTED'; await invite.join();
  assert.equal(invite.data.pending, false); assert.equal(invite.data.rejected, true); assert.equal(joins, 0);
  status = 'JOINED'; await invite.refreshStatus(true); assert.match(destination, /bookId=9/);
});

test('targeted invitation cannot silently turn into a new member; login preserves target', async () => {
  let loggedIn = false, destination = '', joins = 0;
  const invite = page('book-invite', {
    '../../services/auth': { isLoggedIn: () => loggedIn },
    '../../services/book': { invite: async () => ({ status: 'OPEN', name: '聚餐', members: [] }), joinStatus: async () => ({ status: 'NEW' }), join: async () => { joins++; return { status: 'JOINED', bookId: 9 }; } }
  }, { navigateTo: ({ url }) => { destination = url; }, showToast() {}, redirectTo() {} });
  invite.onLoad({ code: 'abc', memberId: '12' }); await invite.load(); await invite.join();
  assert.match(decodeURIComponent(destination), /memberId=12/);
  loggedIn = true; await invite.join(); assert.equal(joins, 0); assert.equal(invite.data.unavailable, true);
  invite.select({ detail: { value: 0 } }); await invite.join(); assert.equal(joins, 1);
});

test('book list ignores stale responses after switching archive filter', async () => {
  const resolvers = [];
  const list = page('book-list', { '../../services/auth': { isLoggedIn: () => true }, '../../services/book': { list: () => new Promise(resolve => resolvers.push(resolve)) } });
  const first = list.load(true); list.setData({ status: 'ARCHIVED' }); const second = list.load(true);
  resolvers[1]({ items: [{ bookId: 2, name: '已归档', totalAmount: '10', myNetAmount: '-5' }], hasMore: false }); await second;
  resolvers[0]({ items: [{ bookId: 1, name: '旧请求' }], hasMore: false }); await first;
  assert.equal(list.data.items[0].bookId, 2); assert.equal(list.data.items[0].netText, '应付 ¥5.00');
});

test('expense detail distinguishes account identity from book member identity', () => {
  const detail = page('expense-item-detail', {});
  const result = detail.normalizeDetail({ expenseTime: '2026-09-08T18:00:00', createdBy: 900, amount: '10', shares: [{ userId: 11, nickname: '我', shareAmount: '10' }] }, 11, 900);
  assert.equal(result.creatorDisplayName, '我'); assert.equal(result.shares[0].isCurrentUser, true);
});
