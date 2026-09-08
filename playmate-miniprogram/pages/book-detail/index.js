const books = require('../../services/book');
const { isLoggedIn } = require('../../services/auth');
const { money, netText, invitePath, settlementText } = require('../../utils/book-ui');
const categories = { FOOD: '餐饮', TRANSPORT: '交通', LODGING: '住宿', TICKET: '门票', ENTERTAINMENT: '娱乐', SHOPPING: '购物', OTHER: '其他' };
Page({
  data: { bookId: '', dashboard: null, tab: 'BILLS', bills: [], page: 1, hasMore: false, loading: true, loadingMore: false, error: '', busy: false, category: '', categories: [{ value: '', label: '全部' }, ...Object.keys(categories).map(value => ({ value, label: categories[value] }))] },
  onLoad(options) { this.setData({ bookId: options.bookId || '' }); },
  onShow() { if (!isLoggedIn()) { wx.navigateTo({ url: `/pages/login/index?redirect=${encodeURIComponent(`/pages/book-detail/index?bookId=${this.data.bookId}`)}` }); return; } this.load(); },
  onPullDownRefresh() { return this.load().finally(() => wx.stopPullDownRefresh()); },
  onReachBottom() { if (this.data.tab === 'BILLS' && this.data.hasMore && !this.data.loading && !this.data.loadingMore) this.more(); },
  async load() {
    const generation = (this.generation || 0) + 1; this.generation = generation;
    this.setData({ loading: true, error: '' });
    try {
      const [dashboard, result] = await Promise.all([books.dashboard(this.data.bookId), books.expenses(this.data.bookId, 1, this.data.category)]);
      if (generation !== this.generation) return;
      this.setData({ dashboard: { ...dashboard, totalText: money(dashboard.totalAmount), myNetText: netText(dashboard.myNetAmount),
        members: dashboard.members.map(m => ({ ...m, paidText: money(m.paidAmount), shareText: money(m.shareAmount), netText: netText(m.netAmount) })),
        suggestions: dashboard.suggestions.map(s => ({ ...s, suggestionKey: `${s.fromMemberId}-${s.toMemberId}`, amountText: money(s.amount) })) },
        bills: this.billViews(result.items), page: 1, hasMore: result.hasMore });
      wx.setNavigationBarTitle({ title: dashboard.book.name });
    } catch (error) { if (generation === this.generation) this.setData({ error: error.message || '账本加载失败' }); }
    finally { if (generation === this.generation) this.setData({ loading: false }); }
  },
  billViews(items) { return items.map(e => ({ ...e, amountText: money(e.amount), categoryText: categories[e.category], dateText: String(e.expenseTime).replace('T', ' ').slice(5,16) })); },
  async more() {
    const generation = this.generation; this.setData({ loadingMore: true });
    try { const result = await books.expenses(this.data.bookId, this.data.page + 1, this.data.category);
      if (generation === this.generation) this.setData({ bills: this.data.bills.concat(this.billViews(result.items)), page: this.data.page + 1, hasMore: result.hasMore });
    } catch (error) { wx.showToast({ title: error.message, icon: 'none' }); }
    finally { this.setData({ loadingMore: false }); }
  },
  tab(event) { this.setData({ tab: event.currentTarget.dataset.tab }); },
  filter(event) { this.setData({ category: event.currentTarget.dataset.category }); this.load(); },
  createExpense() { wx.navigateTo({ url: `/pages/expense-edit/index?bookId=${this.data.bookId}` }); },
  expense(event) { wx.navigateTo({ url: `/pages/expense-item-detail/index?bookId=${this.data.bookId}&expenseId=${event.currentTarget.dataset.id}` }); },
  async mutate(action) {
    if (this.data.busy) return; this.setData({ busy: true });
    try { await action(); await this.load(); }
    catch (error) { wx.showToast({ title: error.message || '操作失败', icon: 'none' }); if ((error.message || '').includes('刷新')) await this.load(); }
    finally { this.setData({ busy: false }); }
  },
  addMember() {
    wx.showModal({ title: '添加参与人', editable: true, placeholderText: '输入昵称（最多40字）', success: r => {
      if (!r.confirm) return; const name = (r.content || '').trim();
      if (!name || name.length > 40) { wx.showToast({ title: '请输入1～40字昵称', icon: 'none' }); return; }
      this.mutate(() => books.addMember(this.data.bookId, name));
    } });
  },
  memberMenu(event) {
    const member = this.data.dashboard.members.find(m => String(m.memberId) === String(event.currentTarget.dataset.id));
    wx.showActionSheet({ itemList: ['修改昵称', member.status === 'ACTIVE' ? '停用成员（保留账目）' : '恢复成员'], success: r => {
      if (r.tapIndex === 0) wx.showModal({ title: '修改账本内昵称', editable: true, content: member.nickname, success: result => {
        if (!result.confirm) return; const name = (result.content || '').trim();
        if (!name || name.length > 40) { wx.showToast({ title: '请输入1～40字昵称', icon: 'none' }); return; }
        this.mutate(() => books.renameMember(this.data.bookId, member.memberId, name));
      } });
      else wx.showModal({ title: member.status === 'ACTIVE' ? '停用成员' : '恢复成员', content: '历史账目会保留。停用后不再参与新消费，也不能访问账本。', success: result => {
        if (result.confirm) this.mutate(() => books.memberState(this.data.bookId, member.memberId, member.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'));
      } });
    } });
  },
  decide(event) {
    const { id, approve } = event.currentTarget.dataset;
    const accepted = approve === true || approve === 'true';
    wx.showModal({ title: accepted ? '确认关联本人' : '拒绝关联申请', content: accepted ? '请确认申请人就是该成员本人。关联后将获得账本访问权限，历史账目保持原样。' : '拒绝后，对方可以重新选择成员申请。', success: r => {
      if (r.confirm) this.mutate(() => books.decide(this.data.bookId, id, accepted));
    } });
  },
  rename() {
    wx.showModal({ title: '修改账本名称', editable: true, content: this.data.dashboard.book.name, success: r => {
      if (!r.confirm) return; const name = (r.content || '').trim();
      if (!name || name.length > 80) { wx.showToast({ title: '请输入1～80字名称', icon: 'none' }); return; }
      this.mutate(() => books.rename(this.data.bookId, { name, version: this.data.dashboard.book.version }));
    } });
  },
  archive() {
    const book = this.data.dashboard.book; const status = book.status === 'OPEN' ? 'ARCHIVED' : 'OPEN';
    wx.showModal({ title: status === 'ARCHIVED' ? '归档账本' : '重新打开账本', content: status === 'ARCHIVED' ? '归档后账本只读，仍可查看和复制结果。归档不代表实际转账已完成。' : '重新打开后可以继续添加和修改消费。', success: r => {
      if (r.confirm) this.mutate(() => books.state(this.data.bookId, { status, version: book.version }));
    } });
  },
  copy() { wx.setClipboardData({ data: settlementText(this.data.dashboard) }); },
  onShareAppMessage(event = {}) {
    if (!this.data.dashboard) return { title: '一起算账', path: '/pages/book-list/index' };
    const memberId = event.target && event.target.dataset.memberId;
    const book = this.data.dashboard.book;
    return { title: `一起算账：${book.name}`, path: invitePath(book.shareCode, memberId) };
  },
  home() { wx.switchTab({ url: '/pages/book-list/index' }); }
});
