const books = require('../../services/book');
const { isLoggedIn } = require('../../services/auth');
const { money, netText } = require('../../utils/book-ui');
Page({
  data: { status: 'OPEN', items: [], page: 1, hasMore: false, loading: false, error: '', loggedIn: false },
  onShow() { const bar = this.getTabBar && this.getTabBar(); if (bar) bar.setData({ selected: 1 }); this.load(true); },
  onPullDownRefresh() { return this.load(true).finally(() => wx.stopPullDownRefresh()); },
  onReachBottom() { if (this.data.hasMore && !this.data.loading) this.load(false); },
  async load(reset) {
    const generation = (this.generation || 0) + 1; this.generation = generation;
    const loggedIn = isLoggedIn(); this.setData({ loggedIn });
    if (!loggedIn) { this.setData({ items: [], loading: false, error: '' }); return; }
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, error: '' });
    try {
      const result = await books.list(this.data.status, page);
      if (generation !== this.generation) return;
      const items = result.items.map(b => ({ ...b, totalText: money(b.totalAmount), netText: netText(b.myNetAmount) }));
      this.setData({ items: reset ? items : this.data.items.concat(items), page, hasMore: result.hasMore });
    } catch (error) { if (generation === this.generation) this.setData({ error: error.message }); }
    finally { if (generation === this.generation) this.setData({ loading: false }); }
  },
  filter(event) { this.setData({ status: event.currentTarget.dataset.status, items: [] }); this.load(true); },
  open(event) { wx.navigateTo({ url: `/pages/book-detail/index?bookId=${event.currentTarget.dataset.id}` }); },
  create() { if (!this.data.loggedIn) { this.login(); return; } wx.navigateTo({ url: '/pages/book-create/index' }); },
  login() { wx.navigateTo({ url: `/pages/login/index?redirect=${encodeURIComponent('/pages/book-list/index')}` }); },
  retry() { this.load(true); }
});
