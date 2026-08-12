const expense = require('../../services/expense');
const EXPENSE_CATEGORY = { TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', FOOD: '餐饮', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };

Page({
  data: { activityId: '', activeTab: 'BILLS', loading: true, dashboard: null, summary: null, bills: [], members: [], suggestions: [], categories: [{ value: '', label: '全部' }, { value: 'TRANSPORT', label: '交通' }, { value: 'LODGING', label: '住宿' }, { value: 'TICKET', label: '门票' }, { value: 'FOOD', label: '餐饮' }, { value: 'ENTERTAINMENT', label: '娱乐' }, { value: 'SHOPPING', label: '购物' }, { value: 'OTHER', label: '其他' }], activeCategory: '', errorMessage: '' },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try { const [dashboard, bills] = await Promise.all([expense.getExpenseDashboard(this.data.activityId), expense.getExpenses(this.data.activityId, this.data.activeCategory)]); this.setData({ dashboard, summary: dashboard.summary, bills: this.mapBills(bills), members: dashboard.members || [], suggestions: dashboard.suggestions || [] }); }
    catch (error) { this.setData({ errorMessage: error.message || '费用数据加载失败' }); } finally { this.setData({ loading: false }); }
  },
  tab(e) { this.setData({ activeTab: e.currentTarget.dataset.tab }); },
  async category(e) { this.setData({ activeCategory: e.currentTarget.dataset.category }); try { this.setData({ bills: this.mapBills(await expense.getExpenses(this.data.activityId, this.data.activeCategory)) }); } catch (error) { wx.showToast({ title: error.message || '账单加载失败', icon: 'none' }); } },
  mapBills(bills) { return (bills || []).map(item => ({ ...item, categoryText: EXPENSE_CATEGORY[item.category] || item.category })); },
  create() { wx.navigateTo({ url: `/pages/expense-edit/index?activityId=${this.data.activityId}` }); },
  detail(e) { wx.navigateTo({ url: `/pages/expense-item-detail/index?activityId=${this.data.activityId}&expenseId=${e.currentTarget.dataset.id}` }); },
  back() { wx.navigateBack(); }
});
