const books = require('../../services/book');
const { isLoggedIn } = require('../../services/auth');
const { invitePath } = require('../../utils/book-ui');
Page({
  data: { code: '', memberId: '', selectedIndex: 0, options: [], invite: null, loading: true, busy: false, pending: false, rejected: false, unavailable: false, joinedBookId: '', error: '' },
  onLoad(options) { this.setData({ code: options.code || '', memberId: options.memberId || '' }); },
  onShow() { this.load(); },
  async load() {
    this.setData({ loading: true, error: '' });
    try {
      if (!this.data.code) throw new Error('邀请链接不完整');
      const invite = await books.invite(this.data.code);
      const options = [{ memberId: '', nickname: '我是新成员' }, ...invite.members];
      const selectedIndex = Math.max(0, options.findIndex(m => String(m.memberId) === String(this.data.memberId)));
      const unavailable = !!this.data.memberId && !options.some(m => String(m.memberId) === String(this.data.memberId));
      this.setData({ invite, options, selectedIndex, unavailable });
      if (isLoggedIn()) await this.refreshStatus(false);
    } catch (error) { this.setData({ error: error.message || '邀请已失效' }); }
    finally { this.setData({ loading: false }); }
  },
  select(event) { const selectedIndex = Number(event.detail.value); this.setData({ selectedIndex, unavailable: false, memberId: this.data.options[selectedIndex].memberId }); },
  async refreshStatus(navigate) {
    const result = await books.joinStatus(this.data.code);
    this.setData({ pending: result.status === 'PENDING', rejected: result.status === 'REJECTED', joinedBookId: result.status === 'JOINED' ? result.bookId : '' });
    if (navigate && result.status === 'JOINED') wx.redirectTo({ url: `/pages/book-detail/index?bookId=${result.bookId}` });
    return result;
  },
  async join() {
    if (this.data.busy) return;
    if (!isLoggedIn()) { wx.navigateTo({ url: `/pages/login/index?redirect=${encodeURIComponent(invitePath(this.data.code, this.data.memberId))}` }); return; }
    this.setData({ busy: true });
    try {
      if (this.data.pending || this.data.joinedBookId) { await this.refreshStatus(true); return; }
      if (this.data.unavailable) { wx.showToast({ title: '该成员已关联，请重新选择身份', icon: 'none' }); return; }
      const choice = this.data.options[this.data.selectedIndex];
      const result = await books.join({ code: this.data.code, memberId: choice.memberId || null });
      if (result.status === 'JOINED') wx.redirectTo({ url: `/pages/book-detail/index?bookId=${result.bookId}` });
      else this.setData({ pending: true });
    } catch (error) { wx.showToast({ title: error.message || '加入失败', icon: 'none' }); }
    finally { this.setData({ busy: false }); }
  },
  home() { wx.switchTab({ url: '/pages/book-list/index' }); }
});
