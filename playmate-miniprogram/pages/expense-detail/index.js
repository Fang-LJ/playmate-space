const expense = require('../../services/expense');
const EXPENSE_CATEGORY = { TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', FOOD: '餐饮', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };

Page({
  data: { activityId: '', activeTab: 'BILLS', loading: true, summary: null, bills: [], members: [], settlements: [], history: [], categories: [{ value: '', label: '全部' }, { value: 'TRANSPORT', label: '交通' }, { value: 'LODGING', label: '住宿' }, { value: 'TICKET', label: '门票' }, { value: 'FOOD', label: '餐饮' }, { value: 'ENTERTAINMENT', label: '娱乐' }, { value: 'SHOPPING', label: '购物' }, { value: 'OTHER', label: '其他' }], activeCategory: '', errorMessage: '' },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try { const [summary, bills, members, settlement, history] = await Promise.all([expense.getExpenseSummary(this.data.activityId), expense.getExpenses(this.data.activityId, this.data.activeCategory), expense.getExpenseMembers(this.data.activityId), expense.getSettlementSummary(this.data.activityId), expense.getSettlementHistory(this.data.activityId)]); this.setData({ summary, bills: (bills || []).map(item => ({ ...item, categoryText: EXPENSE_CATEGORY[item.category] || item.category })), members, settlements: settlement.suggestions || [], history: history || [] }); }
    catch (error) { this.setData({ errorMessage: error.message || '费用数据加载失败' }); } finally { this.setData({ loading: false }); }
  },
  tab(e) { this.setData({ activeTab: e.currentTarget.dataset.tab }); },
  category(e) { this.setData({ activeCategory: e.currentTarget.dataset.category }); this.load(); },
  create() { wx.navigateTo({ url: `/pages/expense-edit/index?activityId=${this.data.activityId}` }); },
  detail(e) { wx.navigateTo({ url: `/pages/expense-item-detail/index?activityId=${this.data.activityId}&expenseId=${e.currentTarget.dataset.id}` }); },
  async complete(e) { const item = e.currentTarget.dataset.item; try { await expense.completeSettlement(this.data.activityId, { fromUserId: item.fromUserId, toUserId: item.toUserId, amount: item.amount }); wx.showToast({ title: '已记录转账', icon: 'success' }); this.load(); } catch (error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); } },
  async cancel(e) { const item = e.currentTarget.dataset.item; try { await expense.cancelSettlement(this.data.activityId, item.settlementId, ''); wx.showToast({ title: '已撤销', icon: 'success' }); this.load(); } catch (error) { wx.showToast({ title: error.message || '撤销失败', icon: 'none' }); } },
  back() { wx.navigateBack(); }
});
