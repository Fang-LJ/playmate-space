const activityExpenses = require('../services/expense');
const books = require('../services/book');
const { getActivityDetail } = require('../services/activity');
const { getCurrentUser } = require('../services/user');

// The shared form historically calls its participant key userId. Only this view adapter
// maps book member IDs to that key; standalone APIs always use explicit memberId fields.
function bookExpenseView(detail) {
  return { ...detail, payerUserId: detail.payerMemberId,
    shares: detail.shares.map(s => ({ ...s, userId: s.memberId })) };
}
function bookExpensePayload(form) {
  const { payerUserId, shares, ...rest } = form;
  return { ...rest, payerMemberId: payerUserId,
    shares: shares.map(({ userId, shareAmount, splitRatio }) => ({ memberId: userId, shareAmount, splitRatio })) };
}
function createExpenseContext(options) {
  if (options.bookId) {
    const id = options.bookId;
    return {
      query: `bookId=${encodeURIComponent(id)}`,
      async loadEditor() {
        const dashboard = await books.dashboard(id);
        if (dashboard.book.status !== 'OPEN') throw new Error('账本已归档，请创建者重新打开后修改');
        return {
          members: dashboard.members.filter(m => options.expenseId || m.status === 'ACTIVE')
            .map(m => ({ ...m, userId: m.memberId, nickname: m.nickname + (m.status === 'INACTIVE' ? '（已停用）' : '') })),
          activity: { currentUserRole: dashboard.isOwner ? 'CREATOR' : 'MEMBER' },
          currentUser: { userId: dashboard.myMemberId }
        };
      },
      async viewer() {
        const [dashboard, account] = await Promise.all([books.dashboard(id), getCurrentUser()]);
        return { userId: dashboard.myMemberId, accountUserId: account.userId };
      },
      getExpense: async expenseId => bookExpenseView(await books.expense(id, expenseId)),
      saveExpense: (data, expenseId) => books.saveExpense(id, bookExpensePayload(data), expenseId),
      voidExpense: (expenseId, data) => books.voidExpense(id, expenseId, data)
    };
  }
  const id = options.activityId;
  return {
    query: `activityId=${encodeURIComponent(id)}`,
    async loadEditor() {
      const [members, activity, currentUser] = await Promise.all([
        activityExpenses.getExpenseMembers(id), getActivityDetail(id), getCurrentUser()
      ]);
      return { members, activity, currentUser };
    },
    viewer: getCurrentUser,
    getExpense: expenseId => activityExpenses.getExpense(id, expenseId),
    saveExpense: (data, expenseId) => activityExpenses.saveExpense(id, data, expenseId),
    voidExpense: (expenseId, data) => activityExpenses.voidExpense(id, expenseId, data)
  };
}
module.exports = { createExpenseContext, bookExpensePayload, bookExpenseView };
