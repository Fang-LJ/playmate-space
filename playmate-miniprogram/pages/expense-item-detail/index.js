const expense = require('../../services/expense');
const { getCurrentUser } = require('../../services/user');

const EXPENSE_CATEGORY = { TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', FOOD: '餐饮', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };
const CATEGORY_THEME = { TRANSPORT: 'theme-transport', LODGING: 'theme-lodging', TICKET: 'theme-ticket', FOOD: 'theme-food', ENTERTAINMENT: 'theme-entertainment', SHOPPING: 'theme-shopping', OTHER: 'theme-other' };

Page({
  data: { activityId: '', expenseId: '', detail: null, loading: true, errorMessage: '' },
  onLoad(options) { this.setData({ activityId: options.activityId || '', expenseId: options.expenseId || '' }); },
  onShow() { if (this.data.expenseId) this.load(); },
  async load() {
    this.setData({ loading: true });
    try {
      const [detail, currentUser] = await Promise.all([
        expense.getExpense(this.data.activityId, this.data.expenseId),
        getCurrentUser()
      ]);
      this.setData({ detail: this.normalizeDetail(detail, currentUser.userId), errorMessage: '' });
    } catch (error) {
      this.setData({ errorMessage: error.message || '账单加载失败' });
    } finally {
      this.setData({ loading: false });
    }
  },
  formatMoney(value) { return Number(value || 0).toFixed(2); },
  datetimeParts(value) {
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return { short: '', full: '' };
    const pad = number => String(number).padStart(2, '0');
    return {
      short: `${date.getMonth() + 1}月${date.getDate()}日 ${pad(date.getHours())}:${pad(date.getMinutes())}`,
      full: `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    };
  },
  normalizeDetail(detail, currentUserId) {
    const datetime = this.datetimeParts(detail.expenseTime);
    const shares = (detail.shares || []).map(item => ({
      ...item,
      shareAmountText: this.formatMoney(item.shareAmount),
      avatarText: (item.nickname || '玩').slice(0, 1),
      isCurrentUser: String(item.userId) === String(currentUserId)
    }));
    return {
      ...detail,
      shares,
      categoryText: EXPENSE_CATEGORY[detail.category] || detail.category,
      categoryTheme: CATEGORY_THEME[detail.category] || 'theme-other',
      amountText: this.formatMoney(detail.amount),
      expenseTimeShort: datetime.short,
      expenseTimeFull: datetime.full,
      statusText: detail.status === 'VOID' ? '已作废账单' : '有效账单',
      creatorDisplayName: String(detail.createdBy) === String(currentUserId) ? '我' : detail.creatorNickname,
      splitModeText: detail.splitMode === 'CUSTOM' ? '自定义分摊' : detail.splitMode === 'PROPORTIONAL' ? '按比例分摊' : '平均分摊',
      shareCount: shares.length
    };
  },
  edit() { wx.navigateTo({ url: `/pages/expense-edit/index?activityId=${this.data.activityId}&expenseId=${this.data.expenseId}` }); },
  voidExpense() { wx.showModal({ title: '作废账单', content: '作废后该账单将不再参与 AA 计算。', confirmColor: '#d94c4c', success: async result => { if (!result.confirm) return; try { await expense.voidExpense(this.data.activityId, this.data.expenseId, { expectedVersion: this.data.detail.version, reason: '' }); wx.showToast({ title: '已作废', icon: 'success' }); wx.navigateBack(); } catch (error) { wx.showToast({ title: error.message || '作废失败', icon: 'none' }); if ((error.message || '').includes('刷新')) this.load(); } } }); },
  back() { wx.navigateBack(); }
});
