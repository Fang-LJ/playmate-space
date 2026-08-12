const expense = require('../../services/expense');
const EXPENSE_CATEGORY = { TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', FOOD: '餐饮', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };
Page({
  data: { activityId: '', expenseId: '', detail: null, loading: true, errorMessage: '' },
  onLoad(options) { this.setData({ activityId: options.activityId || '', expenseId: options.expenseId || '' }); },
  onShow() { if (this.data.expenseId) this.load(); },
  async load() { this.setData({ loading: true }); try { const detail = await expense.getExpense(this.data.activityId, this.data.expenseId); this.setData({ detail: { ...detail, categoryText: EXPENSE_CATEGORY[detail.category] || detail.category }, errorMessage: '' }); } catch (error) { this.setData({ errorMessage: error.message || '账单加载失败' }); } finally { this.setData({ loading: false }); } },
  edit() { wx.navigateTo({ url: `/pages/expense-edit/index?activityId=${this.data.activityId}&expenseId=${this.data.expenseId}` }); },
  voidExpense() { wx.showModal({ title: '删除账单', content: '删除后该账单将不再参与 AA 计算。', confirmColor: '#d94c4c', success: async result => { if (!result.confirm) return; try { await expense.voidExpense(this.data.activityId, this.data.expenseId, ''); wx.showToast({ title: '已删除', icon: 'success' }); wx.navigateBack(); } catch (error) { wx.showToast({ title: error.message || '删除失败', icon: 'none' }); } } }); },
  back() { wx.navigateBack(); }
});
