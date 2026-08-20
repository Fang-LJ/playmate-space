const expense = require('../../services/expense');
const EXPENSE_CATEGORY = { TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', FOOD: '餐饮', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };
const SHARE_TONE = { TRANSPORT: 'share-blue', LODGING: 'share-purple', TICKET: 'share-orange', FOOD: 'share-mint', ENTERTAINMENT: 'share-pink', SHOPPING: 'share-blue', OTHER: 'share-gray' };

Page({
  data: { activityId: '', activeTab: 'BILLS', loading: true, dashboard: null, summary: null, bills: [], members: [], suggestions: [], categories: [{ value: '', label: '全部' }, { value: 'TRANSPORT', label: '交通' }, { value: 'LODGING', label: '住宿' }, { value: 'TICKET', label: '门票' }, { value: 'FOOD', label: '餐饮' }, { value: 'ENTERTAINMENT', label: '娱乐' }, { value: 'SHOPPING', label: '购物' }, { value: 'OTHER', label: '其他' }], activeCategory: '', errorMessage: '' },
  onLoad(options) { this.setData({ activityId: options.activityId || '' }); },
  onShow() { if (this.data.activityId) this.load(); },
  async load() {
    this.setData({ loading: true, errorMessage: '' });
    try {
      const [dashboard, bills] = await Promise.all([expense.getExpenseDashboard(this.data.activityId), expense.getExpenses(this.data.activityId, this.data.activeCategory)]);
      const members = dashboard.members || [];
      const suggestions = this.mapSuggestions(dashboard.suggestions || [], members);
      this.setData({
        dashboard,
        summary: {
          ...dashboard.summary,
          totalExpenseAmountText: this.formatMoney(dashboard.summary.totalExpenseAmount),
          pendingSettlementCount: suggestions.length
        },
        bills: this.mapBills(bills),
        members,
        suggestions
      });
    }
    catch (error) { this.setData({ errorMessage: error.message || '费用数据加载失败' }); } finally { this.setData({ loading: false }); }
  },
  tab(e) { this.setData({ activeTab: e.currentTarget.dataset.tab }); },
  async category(e) { this.setData({ activeCategory: e.currentTarget.dataset.category }); try { this.setData({ bills: this.mapBills(await expense.getExpenses(this.data.activityId, this.data.activeCategory)) }); } catch (error) { wx.showToast({ title: error.message || '账单加载失败', icon: 'none' }); } },
  formatMoney(value) { return Number(value || 0).toFixed(2); },
  formatExpenseTime(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '';
    const pad = number => String(number).padStart(2, '0');
    return `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  },
  mapBills(bills) {
    return (bills || []).map(item => ({
      ...item,
      categoryText: EXPENSE_CATEGORY[item.category] || item.category,
      amountText: this.formatMoney(item.amount),
      expenseTimeText: this.formatExpenseTime(item.expenseTime),
      payerSummary: `${item.payerNickname} · ${item.shareMemberCount || 0} 人均摊`,
      currentUserShareText: item.currentUserShareAmount === null ? '' : this.formatMoney(item.currentUserShareAmount),
      shareTone: SHARE_TONE[item.category] || 'share-gray'
    }));
  },
  mapSuggestions(suggestions, members) {
    const memberById = new Map((members || []).map(item => [String(item.userId), item]));
    return (suggestions || []).map(item => {
      const fromMember = memberById.get(String(item.fromUserId)) || {};
      const toMember = memberById.get(String(item.toUserId)) || {};
      const fromName = item.fromNickname || fromMember.nickname || '成员';
      const toName = item.toNickname || toMember.nickname || '成员';
      return {
        ...item,
        suggestionKey: `${item.fromUserId}-${item.toUserId}`,
        fromNickname: fromName,
        toNickname: toName,
        fromAvatarUrl: item.fromAvatarUrl || fromMember.avatarUrl || '',
        toAvatarUrl: item.toAvatarUrl || toMember.avatarUrl || '',
        fromAvatarText: fromName.slice(0, 1),
        toAvatarText: toName.slice(0, 1),
        amountText: this.formatMoney(item.amount)
      };
    });
  },
  create() { wx.navigateTo({ url: `/pages/expense-edit/index?activityId=${this.data.activityId}` }); },
  detail(e) { wx.navigateTo({ url: `/pages/expense-item-detail/index?activityId=${this.data.activityId}&expenseId=${e.currentTarget.dataset.id}` }); },
  back() { wx.navigateBack(); }
});
