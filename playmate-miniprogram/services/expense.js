const { request } = require('../utils/request');

function base(activityId) { return `/api/activities/${activityId}`; }
function getExpenseSummary(activityId) { return request({ url: `${base(activityId)}/expenses/summary` }); }
function getExpenses(activityId, category) { return request({ url: `${base(activityId)}/expenses`, data: category ? { category } : {} }); }
function getExpense(activityId, expenseId) { return request({ url: `${base(activityId)}/expenses/${expenseId}` }); }
function saveExpense(activityId, data, expenseId) { return request({ url: expenseId ? `${base(activityId)}/expenses/${expenseId}` : `${base(activityId)}/expenses`, method: expenseId ? 'PUT' : 'POST', data }); }
function voidExpense(activityId, expenseId, reason) { return request({ url: `${base(activityId)}/expenses/${expenseId}/void`, method: 'POST', data: { reason } }); }
function getExpenseMembers(activityId) { return request({ url: `${base(activityId)}/expenses/members` }); }
function getSettlementSummary(activityId) { return request({ url: `${base(activityId)}/settlements/summary` }); }
function getSettlementHistory(activityId) { return request({ url: `${base(activityId)}/settlements/history` }); }
function completeSettlement(activityId, data) { return request({ url: `${base(activityId)}/settlements/complete`, method: 'POST', data }); }
function cancelSettlement(activityId, settlementId, reason) { return request({ url: `${base(activityId)}/settlements/${settlementId}/cancel`, method: 'POST', data: { reason } }); }
module.exports = { getExpenseSummary, getExpenses, getExpense, saveExpense, voidExpense, getExpenseMembers, getSettlementSummary, getSettlementHistory, completeSettlement, cancelSettlement };
