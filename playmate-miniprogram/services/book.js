const { request } = require('../utils/request');
const base = id => `/api/books/${id}`;
const call = (url, method = 'GET', data) => request({ url, method, data });
module.exports = {
  list: (status = 'OPEN', page = 1) => call('/api/books', 'GET', { status, page }),
  create: data => call('/api/books', 'POST', data),
  dashboard: id => call(base(id)),
  rename: (id, data) => call(base(id), 'PUT', data),
  state: (id, data) => call(`${base(id)}/state`, 'POST', data),
  addMember: (id, nickname) => call(`${base(id)}/members`, 'POST', { nickname }),
  renameMember: (id, memberId, nickname) => call(`${base(id)}/members/${memberId}`, 'PUT', { nickname }),
  memberState: (id, memberId, status) => call(`${base(id)}/members/${memberId}/state`, 'POST', { status }),
  invite: code => request({ url: `/api/book-invites/${encodeURIComponent(code)}`, requireAuth: false }),
  joinStatus: code => call('/api/books/join-status', 'GET', { code }),
  join: data => call('/api/books/join', 'POST', data),
  decide: (id, claimId, approve) => call(`${base(id)}/claims/${claimId}`, 'POST', { approve }),
  expenses: (id, page = 1, category) => call(`${base(id)}/expenses`, 'GET', { page, ...(category ? { category } : {}) }),
  expense: (id, expenseId) => call(`${base(id)}/expenses/${expenseId}`),
  saveExpense: (id, data, expenseId) => call(expenseId ? `${base(id)}/expenses/${expenseId}` : `${base(id)}/expenses`, expenseId ? 'PUT' : 'POST', data),
  voidExpense: (id, expenseId, data) => call(`${base(id)}/expenses/${expenseId}/void`, 'POST', data)
};
