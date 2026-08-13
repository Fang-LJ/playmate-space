const { request } = require('../utils/request');

function base(activityId) { return `/api/activities/${activityId}`; }
function getExpenseSummary(activityId) { return request({ url: `${base(activityId)}/expenses/summary` }); }
function getExpenseDashboard(activityId) { return request({ url: `${base(activityId)}/expenses/dashboard` }); }
function getExpenses(activityId, category) { return request({ url: `${base(activityId)}/expenses`, data: category ? { category } : {} }); }
function getExpense(activityId, expenseId) { return request({ url: `${base(activityId)}/expenses/${expenseId}` }); }
function saveExpense(activityId, data, expenseId) { return request({ url: expenseId ? `${base(activityId)}/expenses/${expenseId}` : `${base(activityId)}/expenses`, method: expenseId ? 'PUT' : 'POST', data }); }
function voidExpense(activityId, expenseId, data) { return request({ url: `${base(activityId)}/expenses/${expenseId}/void`, method: 'POST', data }); }
function getExpenseMembers(activityId) { return request({ url: `${base(activityId)}/expenses/members` }); }
module.exports = { getExpenseSummary, getExpenseDashboard, getExpenses, getExpense, saveExpense, voidExpense, getExpenseMembers };
